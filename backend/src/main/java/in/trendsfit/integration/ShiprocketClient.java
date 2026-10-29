package in.trendsfit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import in.trendsfit.config.AppConfig;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Shiprocket REST client. Credentials come from environment variables only.
 * Nothing here invents couriers, rates or dates: every value is passed through from the provider's response.
 * NOTE: written from Shiprocket's public API documentation; it must be verified once against a real account (see docs/LIVE_TESTING.md).
 */
@Component
public class ShiprocketClient implements ShippingGateway {
    private final AppConfig cfg;
    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public ShiprocketClient(AppConfig cfg) { this.cfg = cfg; }

    @Override
    public boolean configured() { return cfg.shiprocketConfigured(); }

    private RestClient base() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(20000);
        return RestClient.builder().requestFactory(f).baseUrl(cfg.shiprocketBaseUrl).build();
    }

    private synchronized String token() {
        if (!configured()) throw ApiException.unavailable("Shipping is not configured.");
        if (token != null && Instant.now().isBefore(tokenExpiry)) return token;
        try {
            JsonNode n = base().post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", cfg.shiprocketEmail, "password", cfg.shiprocketPassword)).retrieve().body(JsonNode.class);
            if (n == null || !n.hasNonNull("token")) throw bad("Shipping provider login failed.");
            token = n.get("token").asText();
            tokenExpiry = Instant.now().plusSeconds(8L * 24 * 3600);
            return token;
        } catch (RestClientException e) {
            throw bad("Shipping provider login failed.");
        }
    }

    private RestClient authed() {
        return base().mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token()).build();
    }

    private static ApiException bad(String m) { return new ApiException(HttpStatus.BAD_GATEWAY, "SHIPPING_PROVIDER", m); }

    @Override
    public List<Courier> serviceability(String pincode, boolean cod, BigDecimal orderValue) {
        if (cfg.pickupPostcode == null || cfg.pickupPostcode.isBlank()) throw ApiException.unavailable("Pickup pincode is not configured.");
        try {
            JsonNode n = authed().get().uri(u -> u.path("/courier/serviceability/")
                .queryParam("pickup_postcode", cfg.pickupPostcode).queryParam("delivery_postcode", pincode)
                .queryParam("weight", cfg.defaultWeightKg).queryParam("cod", cod ? 1 : 0).build())
                .retrieve().body(JsonNode.class);
            List<Courier> out = new ArrayList<>();
            if (n != null) for (JsonNode c : n.path("data").path("available_courier_companies")) {
                out.add(new Courier(c.path("courier_name").asText(), c.has("rate") ? new BigDecimal(c.get("rate").asText("0")) : null,
                    c.has("estimated_delivery_days") ? c.get("estimated_delivery_days").asInt() : null, c.path("etd").asText(null)));
            }
            return out;
        } catch (RestClientException e) {
            throw bad("Could not check delivery for this pincode right now.");
        }
    }

    @Override
    public Created createOrder(Parcel p) {
        return post("/orders/create/adhoc", payload(p, false));
    }

    @Override
    public Created createReturn(Parcel p) {
        return post("/orders/create/return", payload(p, true));
    }

    private Map<String, Object> payload(Parcel p, boolean ret) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", p.orderNumber());
        m.put("order_date", p.orderDate());
        if (ret) {
            m.put("pickup_customer_name", p.customerName());
            m.put("pickup_address", p.address());
            m.put("pickup_city", p.city());
            m.put("pickup_state", p.state());
            m.put("pickup_country", "India");
            m.put("pickup_pincode", p.pincode());
            m.put("pickup_email", p.email());
            m.put("pickup_phone", p.phone());
            m.put("shipping_customer_name", cfg.businessName);
            m.put("shipping_address", cfg.businessAddress);
            m.put("shipping_city", "");
            m.put("shipping_country", "India");
            m.put("shipping_pincode", cfg.pickupPostcode);
            m.put("shipping_state", "");
            m.put("shipping_phone", cfg.businessWhatsapp);
        } else {
            m.put("pickup_location", cfg.pickupLocation);
            m.put("billing_customer_name", p.customerName());
            m.put("billing_last_name", "");
            m.put("billing_address", p.address());
            m.put("billing_city", p.city());
            m.put("billing_pincode", p.pincode());
            m.put("billing_state", p.state());
            m.put("billing_country", "India");
            m.put("billing_email", p.email());
            m.put("billing_phone", p.phone());
            m.put("shipping_is_billing", true);
            m.put("payment_method", p.cod() ? "COD" : "Prepaid");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ParcelItem i : p.items()) items.add(Map.of("name", i.name(), "sku", i.sku(), "units", i.units(), "selling_price", i.price()));
        m.put("order_items", items);
        m.put("sub_total", p.subTotal());
        m.put("length", cfg.parcelLengthCm);
        m.put("breadth", cfg.parcelBreadthCm);
        m.put("height", cfg.parcelHeightCm);
        m.put("weight", cfg.defaultWeightKg);
        return m;
    }

    private Created post(String path, Map<String, Object> body) {
        try {
            JsonNode n = authed().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if (n == null || !n.hasNonNull("shipment_id")) throw bad("Shipping provider did not create the shipment.");
            return new Created(n.path("order_id").asText(null), n.path("shipment_id").asText());
        } catch (RestClientException e) {
            throw bad("Shipping provider rejected the shipment. Check the order address and pickup settings.");
        }
    }

    @Override
    public Awb assignAwb(String srShipmentId) {
        try {
            JsonNode n = authed().post().uri("/courier/assign/awb").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("shipment_id", srShipmentId)).retrieve().body(JsonNode.class);
            JsonNode d = n == null ? null : n.path("response").path("data");
            if (d == null || !d.hasNonNull("awb_code") || d.path("awb_code").asText().isBlank())
                throw bad("Courier was not assigned. Check wallet balance and courier settings in Shiprocket.");
            return new Awb(d.get("awb_code").asText(), d.path("courier_name").asText(null));
        } catch (RestClientException e) {
            throw bad("Courier assignment failed.");
        }
    }

    @Override
    public String labelUrl(String srShipmentId) {
        try {
            JsonNode n = authed().post().uri("/courier/generate/label").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("shipment_id", List.of(srShipmentId))).retrieve().body(JsonNode.class);
            String url = n == null ? null : n.path("label_url").asText(null);
            if (url == null || url.isBlank()) throw bad("Label is not available yet.");
            return url;
        } catch (RestClientException e) {
            throw bad("Could not generate the label.");
        }
    }
}
