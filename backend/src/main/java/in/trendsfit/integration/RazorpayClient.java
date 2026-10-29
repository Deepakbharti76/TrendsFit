package in.trendsfit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import in.trendsfit.config.AppConfig;
import in.trendsfit.web.ApiException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin Razorpay REST client (no SDK). Every call fails with "not configured" unless key id and secret are provided. */
@Component
public class RazorpayClient implements PaymentGateway {
    private final AppConfig cfg;

    public RazorpayClient(AppConfig cfg) { this.cfg = cfg; }

    private RestClient http() {
        if (!cfg.razorpayConfigured()) throw ApiException.unavailable("Online payment is not configured.");
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(15000);
        String basic = java.util.Base64.getEncoder().encodeToString((cfg.razorpayKeyId + ":" + cfg.razorpayKeySecret).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return RestClient.builder().requestFactory(f).baseUrl(cfg.razorpayBaseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic).build();
    }

    @Override
    public String createOrder(long amountPaise, String receipt) {
        try {
            JsonNode n = http().post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "payment_capture", 1))
                .retrieve().body(JsonNode.class);
            if (n == null || !n.hasNonNull("id")) throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER", "Payment provider returned an unexpected response.");
            return n.get("id").asText();
        } catch (RestClientException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER", "Could not start the payment. Please try again.");
        }
    }

    @Override
    public String refund(String paymentId, long amountPaise) {
        try {
            JsonNode n = http().post().uri("/payments/{id}/refund", paymentId).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise)).retrieve().body(JsonNode.class);
            return n == null ? null : n.path("id").asText(null);
        } catch (RestClientException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER", "Refund request failed. Check the Razorpay dashboard.");
        }
    }

    @Override
    public String publicKeyId() { return cfg.razorpayKeyId; }
}
