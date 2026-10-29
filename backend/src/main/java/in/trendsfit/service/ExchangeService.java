package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.ExchangeStatus;
import in.trendsfit.domain.ExchangeWindow;
import in.trendsfit.domain.OrderStatus;
import in.trendsfit.model.Exchange;
import in.trendsfit.model.Order;
import in.trendsfit.model.OrderItem;
import in.trendsfit.repo.ExchangeRepository;
import in.trendsfit.repo.OrderRepository;
import in.trendsfit.web.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exchange requests. There is no cancel and no refund-on-return flow: only exchanges, only within the window after delivery. */
@Service
public class ExchangeService {
    public static final Set<String> REASONS = Set.of("SIZE_ISSUE", "WRONG_ITEM", "DEFECT", "COLOR_DIFFERENT", "OTHER");

    private final OrderService orderService;
    private final OrderRepository orders;
    private final ExchangeRepository exchanges;
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final Clock clock;
    private final AuditService audit;
    private final EmailService mail;

    public ExchangeService(OrderService orderService, OrderRepository orders, ExchangeRepository exchanges, JdbcTemplate jdbc,
                           AppConfig cfg, Clock clock, AuditService audit, EmailService mail) {
        this.orderService = orderService; this.orders = orders; this.exchanges = exchanges; this.jdbc = jdbc;
        this.cfg = cfg; this.clock = clock; this.audit = audit; this.mail = mail;
    }

    public record CreateReq(Long orderItemId, Long newVariantId, String reason, String description, List<String> imageUrls) {}

    @Transactional
    public Map<String, Object> create(String orderNumber, String token, CreateReq r) {
        Order detached = orderService.authorize(orderNumber, token);
        Order o = orders.findById(detached.id).orElseThrow(() -> ApiException.notFound("Order not found."));
        ExchangeWindow.Result w = ExchangeWindow.check(o.deliveredAt, LocalDateTime.now(clock), cfg.exchangeWindowDays);
        if (!w.eligible()) throw ApiException.badRequest(w.state() == ExchangeWindow.State.EXPIRED ? "EXCHANGE_EXPIRED" : "NOT_DELIVERED", w.message());
        if (r == null || r.orderItemId() == null || r.newVariantId() == null || r.reason() == null || !REASONS.contains(r.reason()))
            throw ApiException.badRequest("BAD_REQUEST", "Choose the item, the reason and the replacement size or colour.");
        OrderItem item = o.items.stream().filter(i -> i.id.equals(r.orderItemId())).findFirst()
            .orElseThrow(() -> ApiException.badRequest("BAD_ITEM", "That item is not part of this order."));
        if (exchanges.findByOrderIdOrderByIdDesc(o.id).stream().anyMatch(e -> ExchangeStatus.valueOf(e.status).isOpen()))
            throw ApiException.conflict("EXCHANGE_OPEN", "An exchange is already in progress for this order.");
        if (item.variantId.equals(r.newVariantId())) throw ApiException.badRequest("SAME_VARIANT", "Choose a different size or colour.");
        Long sameProduct = jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE id = ? AND product_id = ? AND active = 1 AND stock > 0", Long.class, r.newVariantId(), item.productId);
        if (sameProduct == null || sameProduct == 0)
            throw ApiException.badRequest("VARIANT_UNAVAILABLE", "The selected size or colour is not available for this product right now.");
        if (r.imageUrls() != null && r.imageUrls().size() > 5) throw ApiException.badRequest("TOO_MANY_IMAGES", "Attach at most 5 photos.");
        Exchange e = new Exchange();
        e.orderId = o.id;
        e.orderItemId = item.id;
        e.newVariantId = r.newVariantId();
        e.reason = r.reason();
        e.description = r.description() == null ? null : (r.description().length() > 480 ? r.description().substring(0, 480) : r.description());
        e = exchanges.save(e);
        if (r.imageUrls() != null) for (String u : r.imageUrls()) {
            if (u != null && u.startsWith("/uploads/") && u.length() < 400)
                jdbc.update("INSERT INTO exchange_images (exchange_id, url) VALUES (?,?)", e.id, u);
        }
        orderService.changeStatus(o, OrderStatus.EXCHANGE_REQUESTED, "Exchange requested: " + r.reason());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("status", e.status);
        return m;
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        String sql = "SELECT e.id, e.status, e.reason, e.description, e.admin_note AS adminNote, e.created_at AS createdAt, o.order_number AS orderNumber, "
            + "o.customer_name AS customerName, i.name AS itemName, i.size AS oldSize, i.color AS oldColor, v.size AS newSize, v.color AS newColor "
            + "FROM exchanges e JOIN orders o ON o.id=e.order_id JOIN order_items i ON i.id=e.order_item_id JOIN product_variants v ON v.id=e.new_variant_id";
        if (status != null && !status.isBlank()) return jdbc.queryForList(sql + " WHERE e.status = ? ORDER BY e.id DESC LIMIT 200", status);
        return jdbc.queryForList(sql + " ORDER BY e.id DESC LIMIT 200");
    }

    /** Admin moves an exchange along its own lifecycle; the order status follows for approve / reject / complete. */
    @Transactional
    public void transition(long id, String target, String note) {
        Exchange e = exchanges.findById(id).orElseThrow(() -> ApiException.notFound("Exchange not found."));
        ExchangeStatus to;
        try {
            to = ExchangeStatus.valueOf(target);
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("BAD_STATUS", "Unknown exchange status.");
        }
        ExchangeStatus cur = ExchangeStatus.valueOf(e.status);
        if (!cur.canMoveTo(to)) throw ApiException.conflict("BAD_TRANSITION", "Cannot move an exchange from " + cur + " to " + to + ".");
        Order o = orders.findById(e.orderId).orElseThrow();
        if (to == ExchangeStatus.APPROVED) {
            // Hold the replacement unit. Fails with OUT_OF_STOCK (and nothing changes) if it is gone.
            orderService.reserve(e.newVariantId, 1, "EXCHANGE_RESERVED", o.id, "Replacement item");
            orderService.changeStatus(o, OrderStatus.EXCHANGE_APPROVED, note);
        } else if (to == ExchangeStatus.REJECTED) {
            orderService.changeStatus(o, OrderStatus.EXCHANGE_REJECTED, note);
        } else if (to == ExchangeStatus.COMPLETED) {
            orderService.changeStatus(o, OrderStatus.EXCHANGE_COMPLETED, note);
        } else {
            orderService.history(o.id, o.status, "Exchange " + to.name().toLowerCase().replace('_', ' ') + (note == null || note.isBlank() ? "" : ": " + note));
        }
        e.status = to.name();
        if (note != null && !note.isBlank()) e.adminNote = note.length() > 480 ? note.substring(0, 480) : note;
        exchanges.save(e);
        audit.log("EXCHANGE_" + to.name(), "exchange", e.id, note);
        if (to == ExchangeStatus.APPROVED || to == ExchangeStatus.REJECTED || to == ExchangeStatus.COMPLETED || to == ExchangeStatus.INFO_REQUESTED)
            mail.sendStatus(o.email, o.orderNumber, "Exchange " + to.name().toLowerCase().replace('_', ' '), o.accessToken);
    }
}
