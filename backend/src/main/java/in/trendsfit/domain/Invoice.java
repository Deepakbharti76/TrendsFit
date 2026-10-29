package in.trendsfit.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Non-GST invoice / receipt. The business is not GST registered, so this never shows GSTIN, CGST, SGST, IGST or tax maths.
 * Confirm legal invoice requirements before production use.
 */
public final class Invoice {
    public record Item(String name, String sku, String size, String color, int qty, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record Data(String businessName, String businessAddress, String businessEmail, String invoiceNumber, LocalDateTime issuedAt,
                       String orderNumber, String customerName, String customerEmail, String customerPhone, String shipTo,
                       List<Item> items, BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal total,
                       String paymentMethod, String paymentStatus) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private Invoice() {}

    public static String number(int year, long id) { return String.format("TF-%d-%06d", year, id); }

    public static String inr(BigDecimal v) { return "₹" + Money.round(v).toPlainString(); }

    public static String html(Data d) {
        StringBuilder rows = new StringBuilder();
        for (Item i : d.items()) {
            rows.append("<tr><td>").append(Html.esc(i.name())).append("<br><small>SKU ").append(Html.esc(i.sku()))
                .append(" · ").append(Html.esc(i.color())).append(" · ").append(Html.esc(i.size())).append("</small></td>")
                .append("<td class=\"n\">").append(i.qty()).append("</td><td class=\"n\">").append(inr(i.unitPrice()))
                .append("</td><td class=\"n\">").append(inr(i.lineTotal())).append("</td></tr>");
        }
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Invoice " + Html.esc(d.invoiceNumber()) + "</title>"
            + "<style>body{font-family:Arial,Helvetica,sans-serif;color:#111;max-width:760px;margin:24px auto;padding:0 16px}"
            + "h1{margin:0;letter-spacing:.06em}table{width:100%;border-collapse:collapse;margin-top:16px}th,td{padding:8px;border-bottom:1px solid #ddd;text-align:left;font-size:14px}"
            + ".n{text-align:right}.tot td{border:0}.muted{color:#555;font-size:13px}@media print{body{margin:0}}</style></head><body>"
            + "<h1>" + Html.esc(d.businessName()) + "</h1><p class=\"muted\">" + Html.esc(d.businessAddress()) + "<br>" + Html.esc(d.businessEmail()) + "</p>"
            + "<h2>Invoice / Receipt</h2>"
            + "<p><strong>Invoice no:</strong> " + Html.esc(d.invoiceNumber()) + "<br><strong>Date:</strong> " + d.issuedAt().format(DATE)
            + "<br><strong>Order ID:</strong> " + Html.esc(d.orderNumber()) + "</p>"
            + "<p><strong>Billed to:</strong> " + Html.esc(d.customerName()) + "<br>" + Html.esc(d.customerEmail()) + " · " + Html.esc(d.customerPhone())
            + "<br><strong>Ship to:</strong> " + Html.esc(d.shipTo()) + "</p>"
            + "<table><thead><tr><th>Product</th><th class=\"n\">Qty</th><th class=\"n\">Price</th><th class=\"n\">Amount</th></tr></thead><tbody>" + rows + "</tbody>"
            + "<tfoot><tr class=\"tot\"><td colspan=\"3\" class=\"n\">Subtotal</td><td class=\"n\">" + inr(d.subtotal()) + "</td></tr>"
            + "<tr class=\"tot\"><td colspan=\"3\" class=\"n\">Discount</td><td class=\"n\">-" + inr(d.discount()) + "</td></tr>"
            + "<tr class=\"tot\"><td colspan=\"3\" class=\"n\">Shipping</td><td class=\"n\">" + (d.shipping().signum() == 0 ? "Free" : inr(d.shipping())) + "</td></tr>"
            + "<tr class=\"tot\"><td colspan=\"3\" class=\"n\"><strong>Grand total</strong></td><td class=\"n\"><strong>" + inr(d.total()) + "</strong></td></tr></tfoot></table>"
            + "<p><strong>Payment:</strong> " + Html.esc(d.paymentMethod()) + " · " + Html.esc(d.paymentStatus()) + "</p>"
            + "<p class=\"muted\">This is a computer-generated receipt. No tax is charged separately.</p></body></html>";
    }
}
