package in.trendsfit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Hmac;
import in.trendsfit.domain.OrderStatus;
import in.trendsfit.domain.Validators;
import in.trendsfit.integration.ShippingGateway;
import in.trendsfit.model.Order;
import in.trendsfit.model.OrderItem;
import in.trendsfit.model.Shipment;
import in.trendsfit.repo.OrderRepository;
import in.trendsfit.repo.ShipmentRepository;
import in.trendsfit.web.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shiprocket-backed delivery check, shipment creation, AWB, label and tracking webhooks. Never invents courier data. */
@Service
public class ShippingService {
    private final ShippingGateway gateway;
    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final OrderService orderService;
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final Clock clock;
    private final AuditService audit;
    private final ObjectMapper json;

    public ShippingService(ShippingGateway gateway, OrderRepository orders, ShipmentRepository shipments, OrderService orderService,
                           JdbcTemplate jdbc, AppConfig cfg, Clock clock, AuditService audit, ObjectMapper json) {
        this.gateway = gateway; this.orders = orders; this.shipments = shipments; this.orderService = orderService;
        this.jdbc = jdbc; this.cfg = cfg; this.clock = clock; this.audit = audit; this.json = json;
    }

    /** Public pincode check. When Shiprocket is not configured we say so instead of guessing. */
    public Map<String, Object> checkPincode(String pincode) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!Validators.pincode(pincode)) throw ApiException.badRequest("BAD_PINCODE", "Enter a valid 6-digit pincode.");
        m.put("pincode", pincode.trim());
        if (!gateway.configured() || cfg.pickupPostcode.isBlank()) {
            m.put("checked", false);
            m.put("serviceable", null);
            m.put("message", "Delivery availability and dates will be confirmed after you place your order.");
            return m;
        }
        List<ShippingGateway.Courier> couriers = gateway.serviceability(pincode.trim(), false, null);
        m.put("checked", true);
        m.put("serviceable", !couriers.isEmpty());
        if (couriers.isEmpty()) {
            m.put("message", "Sorry, we cannot deliver to this pincode right now.");
        } else {
            Integer best = couriers.stream().map(ShippingGateway.Courier::etaDays).filter(x -> x != null).min(Integer::compare).orElse(null);
            m.put("estimatedDays", best);
            m.put("message", best == null ? "Delivery is available to this pincode." : "Delivery is available. Estimated delivery in about " + best + " day(s).");
        }
        return m;
    }

    // ---------------------------------------------------------------- admin actions

    /** Creates the Shiprocket order and assigns an AWB. Order must be PROCESSING (paid or COD). */
    @Transactional
    public Map<String, Object> createShipment(String orderNumber) {
        if (!gateway.configured()) throw ApiException.unavailable("Shiprocket is not set up yet. Add SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD to the backend environment and restart the server.");
        Order o = orders.findByOrderNumber(orderNumber).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (o.statusEnum() != OrderStatus.PROCESSING) throw ApiException.conflict("BAD_STATE", "Move the order to Processing before creating a shipment.");
        if (!shipments.findByOrderIdOrderByIdDesc(o.id).stream().noneMatch(s -> s.kind.equals("FORWARD")))
            throw ApiException.conflict("SHIPMENT_EXISTS", "A shipment already exists for this order.");
        List<ShippingGateway.ParcelItem> items = new ArrayList<>();
        for (OrderItem i : o.items) items.add(new ShippingGateway.ParcelItem(i.name + " " + i.size + "/" + i.color, i.sku, i.qty, i.unitPrice));
        String addr = o.shipLine1 + (o.shipLine2 == null ? "" : ", " + o.shipLine2);
        ShippingGateway.Parcel parcel = new ShippingGateway.Parcel(o.orderNumber, LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            o.customerName, o.email, o.phone, addr, o.shipCity, o.shipState, o.shipPincode, o.paymentMethod.equals("COD"), o.total, items);
        ShippingGateway.Created c = gateway.createOrder(parcel);
        ShippingGateway.Awb awb = gateway.assignAwb(c.srShipmentId());
        Shipment s = new Shipment();
        s.orderId = o.id;
        s.srOrderId = c.srOrderId();
        s.srShipmentId = c.srShipmentId();
        s.awb = awb.awb();
        s.courier = awb.courier();
        s.status = "AWB_ASSIGNED";
        s.statusText = "Shipment created";
        s.trackingUrl = "https://shiprocket.co/tracking/" + awb.awb();
        s = shipments.save(s);
        orderService.changeStatus(o, OrderStatus.PACKED, "Shipment created (AWB " + awb.awb() + ")");
        audit.log("SHIPMENT_CREATED", "order", o.orderNumber, "AWB " + awb.awb());
        return shipmentView(s);
    }

    public Map<String, Object> label(long shipmentId) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> ApiException.notFound("Shipment not found."));
        String url = gateway.labelUrl(s.srShipmentId);
        jdbc.update("UPDATE shipments SET label_url = ? WHERE id = ?", url, s.id);
        return Map.of("labelUrl", url);
    }

    public static Map<String, Object> shipmentView(Shipment s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("kind", s.kind);
        m.put("awb", s.awb);
        m.put("courier", s.courier);
        m.put("status", s.status);
        m.put("statusText", s.statusText);
        m.put("trackingUrl", s.trackingUrl);
        m.put("labelUrl", s.labelUrl);
        return m;
    }

    // ---------------------------------------------------------------- webhook

    /** Shiprocket posts tracking updates. Authenticated by a shared token header; never moves an order backwards. */
    @Transactional
    public boolean webhook(String tokenHeader, String rawBody) {
        if (cfg.shiprocketWebhookToken == null || cfg.shiprocketWebhookToken.isBlank())
            throw ApiException.unavailable("Shipping webhook token is not configured.");
        if (tokenHeader == null || !Hmac.safeEquals(tokenHeader, cfg.shiprocketWebhookToken))
            throw ApiException.forbidden("Invalid token.");
        JsonNode n;
        try {
            n = json.readTree(rawBody);
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_PAYLOAD", "Invalid payload.");
        }
        String awb = n.path("awb").asText("");
        String status = n.path("current_status").asText(n.path("shipment_status").asText(""));
        if (awb.isEmpty() || status.isEmpty()) return true;
        String eventId = awb + ":" + status + ":" + n.path("current_timestamp").asText(n.path("scans").size() + "");
        try {
            jdbc.update("INSERT INTO webhook_events (source, event_id, event_type) VALUES ('SHIPROCKET', ?, ?)",
                eventId.length() > 120 ? eventId.substring(0, 120) : eventId, status.length() > 60 ? status.substring(0, 60) : status);
        } catch (DuplicateKeyException dup) {
            return true;
        }
        Shipment s = shipments.findByAwb(awb).orElse(null);
        if (s == null) return true;
        s.statusText = status.length() > 190 ? status.substring(0, 190) : status;
        s.status = norm(status);
        s.lastEventAt = LocalDateTime.now(clock);
        String etd = n.path("etd").asText("");
        if (etd.length() >= 10) {
            try { s.estimatedDelivery = java.time.LocalDate.parse(etd.substring(0, 10)); } catch (RuntimeException ignored) { /* keep old */ }
        }
        shipments.save(s);
        if (!s.kind.equals("FORWARD")) return true;
        Order o = orders.findById(s.orderId).orElse(null);
        if (o == null) return true;
        OrderStatus target = switch (s.status) {
            case "DELIVERED" -> OrderStatus.DELIVERED;
            case "OUT_FOR_DELIVERY" -> OrderStatus.OUT_FOR_DELIVERY;
            case "SHIPPED" -> OrderStatus.SHIPPED;
            default -> null;
        };
        if (target != null && target.rank() > o.statusEnum().rank() && o.statusEnum().rank() < OrderStatus.EXCHANGE_REQUESTED.rank()) {
            advance(o, target, "Courier update: " + status);
        }
        return true;
    }

    /** Walks the lifecycle forward step by step so each transition stays valid (e.g. PACKED -> SHIPPED -> DELIVERED). */
    private void advance(Order o, OrderStatus target, String note) {
        int guard = 0;
        while (o.statusEnum() != target && guard++ < 6) {
            OrderStatus cur = o.statusEnum();
            OrderStatus step = cur.canMoveTo(target) ? target : switch (cur) {
                case PAID -> OrderStatus.PROCESSING;
                case PROCESSING, PACKED -> OrderStatus.SHIPPED;
                case SHIPPED -> OrderStatus.OUT_FOR_DELIVERY;
                default -> null;
            };
            if (step == null || !cur.canMoveTo(step)) return;
            orderService.changeStatus(o, step, step == target ? note : "Courier update");
        }
    }

    static String norm(String s) {
        String t = s.trim().toUpperCase().replace('-', ' ').replace('_', ' ');
        if (t.contains("OUT FOR DELIVERY")) return "OUT_FOR_DELIVERY";
        if (t.contains("DELIVERED") && !t.contains("UNDELIVERED") && !t.contains("RTO")) return "DELIVERED";
        if (t.contains("RTO")) return "RTO";
        if (t.contains("UNDELIVERED") || t.contains("NDR")) return "NDR";
        if (t.contains("SHIPPED") || t.contains("IN TRANSIT") || t.contains("PICKED UP") || t.contains("PICKUP DONE") || t.contains("REACHED")) return "SHIPPED";
        return t.replace(' ', '_').substring(0, Math.min(30, t.length()));
    }
}
