package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Invoice;
import in.trendsfit.model.Order;
import in.trendsfit.model.OrderItem;
import in.trendsfit.repo.OrderRepository;
import in.trendsfit.web.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Non-GST payment receipt / invoice as downloadable HTML (print to PDF in the browser). Numbers are issued once per order. */
@Service
public class InvoiceService {
    private final OrderService orderService;
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final Clock clock;

    public InvoiceService(OrderService orderService, OrderRepository orders, JdbcTemplate jdbc, AppConfig cfg, Clock clock) {
        this.orderService = orderService; this.orders = orders; this.jdbc = jdbc; this.cfg = cfg; this.clock = clock;
    }

    @Transactional
    public String html(String orderNumber, String token) {
        Order detached = orderService.authorize(orderNumber, token);
        Order o = orders.findById(detached.id).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (o.status.equals("PENDING_PAYMENT") || o.status.equals("PAYMENT_FAILED"))
            throw ApiException.conflict("NOT_PAID", "An invoice is available once the order is confirmed.");
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT invoice_number, issued_at FROM invoices WHERE order_id = ?", o.id);
        String number;
        LocalDateTime issued;
        if (rows.isEmpty()) {
            issued = LocalDateTime.now(clock);
            number = Invoice.number(issued.getYear(), o.id);
            try {
                jdbc.update("INSERT INTO invoices (order_id, invoice_number, issued_at) VALUES (?,?,?)", o.id, number, issued);
            } catch (DuplicateKeyException race) {
                java.util.Map<String, Object> r = jdbc.queryForMap("SELECT invoice_number, issued_at FROM invoices WHERE order_id = ?", o.id);
                number = (String) r.get("invoice_number");
                issued = toLdt(r.get("issued_at"));
            }
        } else {
            number = (String) rows.get(0).get("invoice_number");
            issued = toLdt(rows.get(0).get("issued_at"));
        }
        List<Invoice.Item> items = new ArrayList<>();
        for (OrderItem i : o.items) items.add(new Invoice.Item(i.name, i.sku, i.size, i.color, i.qty, i.unitPrice, i.lineTotal));
        String shipTo = o.shipLine1 + (o.shipLine2 == null ? "" : ", " + o.shipLine2) + ", " + o.shipCity + ", " + o.shipState + " - " + o.shipPincode;
        return Invoice.html(new Invoice.Data(cfg.businessName, cfg.businessAddress, cfg.businessEmail, number, issued, o.orderNumber,
            o.customerName, o.email, o.phone, shipTo, items, o.subtotal, o.discount, o.shipping, o.total,
            o.paymentMethod.equals("COD") ? "Cash on Delivery" : "Online (Razorpay)", o.paymentStatus));
    }

    private static LocalDateTime toLdt(Object o) {
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.now();
    }
}
