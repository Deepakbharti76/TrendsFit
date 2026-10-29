package in.trendsfit.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ValidationAndInvoiceTest {
    @Test void indianMobileNormalisation() {
        assertEquals("9876543210", Validators.mobile("+91 98765-43210"));
        assertEquals("9876543210", Validators.mobile("09876543210"));
        assertNull(Validators.mobile("12345"));
        assertNull(Validators.mobile("5876543210"));
        assertNull(Validators.mobile(null));
        assertEquals("9876543210", Validators.mobileExact("9876543210"));
        assertNull(Validators.mobileExact("+919876543210"));
        assertNull(Validators.mobileExact("98765 43210"));
        assertNull(Validators.mobileExact("98765432101"));
        assertNull(Validators.mobileExact("98765abcde"));
        assertNull(Validators.mobileExact(""));
        assertNull(Validators.mobileExact(null));
    }

    @Test void pincodeAndEmail() {
        assertTrue(Validators.pincode("560001"));
        assertFalse(Validators.pincode("060001"));
        assertFalse(Validators.pincode("56000"));
        assertTrue(Validators.email("a.b+c@example.co.in"));
        assertFalse(Validators.email("not-an-email"));
        assertFalse(Validators.email(null));
    }

    @Test void passwordPolicy() {
        assertTrue(Validators.passwordProblems("Str0ngPassw0rd", "a@example.com").isEmpty());
        assertFalse(Validators.passwordProblems("short1A", "a@example.com").isEmpty());
        assertFalse(Validators.passwordProblems("alllowercase123", "a@example.com").isEmpty());
        // The "password must not contain the e-mail name" rule was deliberately removed from Validators by the project owner.
        assertTrue(Validators.passwordProblems("deepak12345Aa", "deepak@example.com").isEmpty());
    }

    @Test void slugs() {
        assertEquals("slim-fit-stretch-jeans", Validators.slugify("  Slim Fit — Stretch Jeans! "));
        assertEquals("item", Validators.slugify("***"));
        assertEquals("cafe", Validators.slugify("Café"));
    }

    @Test void sizeRecommendationIsAnEstimateWithDisclaimer() {
        var r = SizeRecommender.forBottoms(32, "SLIM");
        assertEquals("32", r.size());
        assertEquals("Size recommendation is an estimate. Check the size chart before ordering.", r.disclaimer());
        assertEquals("34", SizeRecommender.forBottoms(32, "LOOSE").size());
        assertEquals("28", SizeRecommender.forBottoms(20, "SLIM").size(), "clamped to the smallest size");
        assertEquals("M", SizeRecommender.forTops(40, "SLIM").size());
        assertEquals("L", SizeRecommender.forTops(40, "RELAXED").size());
    }

    @Test void invoiceHasNoGstAndEscapesCustomerText() {
        var item = new Invoice.Item("Slim <b>Jeans</b>", "TF-JE-001-IND-32", "32", "Indigo", 2, new BigDecimal("1199.00"), new BigDecimal("2398.00"));
        var d = new Invoice.Data("TrendsFit", "Bengaluru, India", "trendsfit.in@gmail.com", Invoice.number(2026, 42), LocalDateTime.of(2026, 10, 15, 9, 0),
                "TF261015AB12", "Asha <script>", "asha@example.com", "9876543210", "12 MG Road, Bengaluru 560001", List.of(item),
                new BigDecimal("2398.00"), new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("2298.00"), "Razorpay", "Paid");
        String html = Invoice.html(d);
        assertTrue(html.contains("TF-2026-000042"));
        assertTrue(html.contains("₹2298.00"));
        assertTrue(html.contains("Free"));
        assertTrue(html.contains("Slim &lt;b&gt;Jeans&lt;/b&gt;"));
        assertFalse(html.contains("<script>"));
        String lower = html.toLowerCase();
        for (String banned : new String[] {"gstin", "cgst", "sgst", "igst", "gst "}) assertFalse(lower.contains(banned), banned);
    }
}
