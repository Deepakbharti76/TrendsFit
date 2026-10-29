package in.trendsfit.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import in.trendsfit.domain.PricingCalculator;

/**
 * Every setting comes from application.yml / environment variables. No secret has a default value.
 * Blank secrets simply switch the matching integration to "not configured" (it never pretends to work).
 */
@Configuration
public class AppConfig {
    @Value("${trendsfit.jwt.secret:}") public String jwtSecret;
    @Value("${trendsfit.jwt.ttl-minutes:720}") public long jwtTtlMinutes;
    @Value("${trendsfit.cors.origins:http://localhost:5173}") public String corsOrigins;
    @Value("${trendsfit.frontend-url:http://localhost:5173}") public String frontendUrl;
    @Value("${trendsfit.public-url:http://localhost:8080}") public String publicUrl;
    @Value("${trendsfit.upload-dir:./uploads}") public String uploadDir;
    @Value("${trendsfit.exchange.window-days:7}") public int exchangeWindowDays;
    @Value("${trendsfit.payment.expiry-minutes:30}") public int paymentExpiryMinutes;
    @Value("${trendsfit.low-stock-threshold:5}") public int lowStockThreshold;
    @Value("${trendsfit.cod.enabled:true}") public boolean codEnabled;
    @Value("${trendsfit.cod.max-order-value:10000}") public BigDecimal codMaxOrderValue;

    @Value("${trendsfit.shipping.free-for-all:true}") public boolean shippingFreeForAll;
    @Value("${trendsfit.shipping.free-above:0}") public BigDecimal shippingFreeAbove;
    @Value("${trendsfit.shipping.flat-rate:0}") public BigDecimal shippingFlatRate;

    @Value("${trendsfit.razorpay.key-id:}") public String razorpayKeyId;
    @Value("${trendsfit.razorpay.key-secret:}") public String razorpayKeySecret;
    @Value("${trendsfit.razorpay.webhook-secret:}") public String razorpayWebhookSecret;
    @Value("${trendsfit.razorpay.base-url:https://api.razorpay.com/v1}") public String razorpayBaseUrl;

    @Value("${trendsfit.shiprocket.email:}") public String shiprocketEmail;
    @Value("${trendsfit.shiprocket.password:}") public String shiprocketPassword;
    @Value("${trendsfit.shiprocket.webhook-token:}") public String shiprocketWebhookToken;
    @Value("${trendsfit.shiprocket.pickup-postcode:}") public String pickupPostcode;
    @Value("${trendsfit.shiprocket.pickup-location:Primary}") public String pickupLocation;
    @Value("${trendsfit.shiprocket.default-weight-kg:0.5}") public double defaultWeightKg;
    @Value("${trendsfit.shiprocket.length-cm:30}") public int parcelLengthCm;
    @Value("${trendsfit.shiprocket.breadth-cm:25}") public int parcelBreadthCm;
    @Value("${trendsfit.shiprocket.height-cm:5}") public int parcelHeightCm;
    @Value("${trendsfit.shiprocket.base-url:https://apiv2.shiprocket.in/v1/external}") public String shiprocketBaseUrl;

    @Value("${trendsfit.business.name:TrendsFit}") public String businessName;
    @Value("${trendsfit.business.address:}") public String businessAddress;
    @Value("${trendsfit.business.email:trendsfit.in@gmail.com}") public String businessEmail;
    @Value("${trendsfit.business.whatsapp:}") public String businessWhatsapp;

    @Value("${trendsfit.admin.bootstrap-email:}") public String adminBootstrapEmail;
    @Value("${trendsfit.admin.bootstrap-password:}") public String adminBootstrapPassword;

    @Value("${trendsfit.mail.enabled:false}") public boolean mailEnabled;
    @Value("${trendsfit.mail.from:no-reply@trendsfit.in}") public String mailFrom;

    @Value("${trendsfit.rate-limit.capacity:60}") public int rateCapacity;
    @Value("${trendsfit.rate-limit.refill-per-second:1.0}") public double rateRefill;
    @Value("${trendsfit.rate-limit.auth-capacity:10}") public int authRateCapacity;
    @Value("${trendsfit.rate-limit.auth-refill-per-second:0.1}") public double authRateRefill;
    @Value("${trendsfit.dev-log-links:false}") public boolean devLogLinks;

    public List<String> corsOriginList() {
        return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public PricingCalculator.ShippingPolicy shippingPolicy() {
        return new PricingCalculator.ShippingPolicy(shippingFreeForAll, shippingFreeAbove, shippingFlatRate);
    }

    public boolean razorpayConfigured() { return !razorpayKeyId.isBlank() && !razorpayKeySecret.isBlank(); }

    public boolean shiprocketConfigured() { return !shiprocketEmail.isBlank() && !shiprocketPassword.isBlank(); }

    @Bean
    public Clock clock() { return Clock.systemDefaultZone(); }
}
