package in.trendsfit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsTest {
    private static String clean(String key, String v) { return Settings.clean(Settings.find(key).orElseThrow(), v); }

    @Test void intsAreRangeChecked() {
        assertEquals("7", clean("exchange.windowDays", " 7 "));
        assertThrows(IllegalArgumentException.class, () -> clean("exchange.windowDays", "0"));
        assertThrows(IllegalArgumentException.class, () -> clean("exchange.windowDays", "99"));
        assertThrows(IllegalArgumentException.class, () -> clean("exchange.windowDays", "abc"));
    }
    @Test void moneyIsNonNegativeAndNormalised() {
        assertEquals("499", clean("shipping.flatRate", "499.00"));
        assertThrows(IllegalArgumentException.class, () -> clean("shipping.flatRate", "-1"));
    }
    @Test void boolsAreStrict() {
        assertEquals("true", clean("cod.enabled", "true"));
        assertThrows(IllegalArgumentException.class, () -> clean("cod.enabled", "yes"));
    }
    @Test void pincodeAndEmailAreValidated() {
        assertEquals("560001", clean("shiprocket.pickupPostcode", "560001"));
        assertThrows(IllegalArgumentException.class, () -> clean("shiprocket.pickupPostcode", "12345"));
        assertThrows(IllegalArgumentException.class, () -> clean("business.email", "not-an-email"));
    }
    @Test void storeNameRequired() {
        assertThrows(IllegalArgumentException.class, () -> clean("business.name", "  "));
    }
    @Test void noSecretsCanBeEdited() {
        assertTrue(Settings.DEFS.stream().noneMatch(d -> d.key().toLowerCase().matches(".*(secret|password|token|key).*")));
        assertTrue(Settings.find("razorpay.keySecret").isEmpty());
    }
}
