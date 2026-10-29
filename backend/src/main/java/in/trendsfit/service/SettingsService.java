package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Settings;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-editable settings. Values saved in app_settings override the environment defaults held in AppConfig.
 * Applied at startup and immediately after each save (no restart). Secrets are never stored here.
 */
@Service
public class SettingsService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final AuditService audit;
    /** Saved values for settings that have no environment-variable field in AppConfig (revenue targets, notification switches). */
    private final java.util.concurrent.ConcurrentHashMap<String, String> extra = new java.util.concurrent.ConcurrentHashMap<>();

    public SettingsService(JdbcTemplate jdbc, AppConfig cfg, AuditService audit) {
        this.jdbc = jdbc; this.cfg = cfg; this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, String> saved = saved();
            saved.forEach(this::apply);
            log.info("Applied {} saved setting(s).", saved.size());
        } catch (Exception e) {
            log.warn("Could not load saved settings: {}", e.getMessage());
        }
    }

    private Map<String, String> saved() {
        Map<String, String> m = new LinkedHashMap<>();
        jdbc.query("SELECT setting_key, setting_value FROM app_settings", rs -> { m.put(rs.getString(1), rs.getString(2)); });
        return m;
    }

    /** Current effective value of every setting, grouped for the admin screen. */
    public List<Map<String, Object>> view() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Settings.Def d : Settings.DEFS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", d.key()); m.put("group", d.group()); m.put("label", d.label());
            m.put("type", d.type().name()); m.put("min", d.min()); m.put("max", d.max());
            m.put("value", current(d.key()));
            out.add(m);
        }
        return out;
    }

    /** All-or-nothing: if any value is invalid nothing is saved. */
    @Transactional
    public void update(Map<String, String> changes, String actorEmail) {
        if (changes == null || changes.isEmpty()) throw ApiException.badRequest("NO_CHANGES", "Nothing to save.");
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : changes.entrySet()) {
            Settings.Def d = Settings.find(e.getKey()).orElseThrow(() -> ApiException.badRequest("UNKNOWN_SETTING", "Unknown setting: " + e.getKey()));
            try { clean.put(d.key(), Settings.clean(d, e.getValue())); }
            catch (IllegalArgumentException ex) { throw ApiException.badRequest("INVALID_SETTING", ex.getMessage()); }
        }
        for (Map.Entry<String, String> e : clean.entrySet()) {
            String before = current(e.getKey());
            jdbc.update("INSERT INTO app_settings (setting_key, setting_value, updated_by) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), updated_by=VALUES(updated_by)", e.getKey(), e.getValue(), actorEmail);
            if (!e.getValue().equals(before)) audit.log("SETTING_CHANGED", "setting", e.getKey(), before + " -> " + e.getValue());
        }
        clean.forEach(this::apply);
    }

    private String current(String key) {
        return switch (key) {
            case "business.name" -> cfg.businessName;
            case "business.email" -> cfg.businessEmail;
            case "business.whatsapp" -> cfg.businessWhatsapp;
            case "business.address" -> cfg.businessAddress;
            case "cod.enabled" -> String.valueOf(cfg.codEnabled);
            case "cod.maxOrderValue" -> cfg.codMaxOrderValue.stripTrailingZeros().toPlainString();
            case "payment.expiryMinutes" -> String.valueOf(cfg.paymentExpiryMinutes);
            case "shipping.freeForAll" -> String.valueOf(cfg.shippingFreeForAll);
            case "shipping.freeAbove" -> cfg.shippingFreeAbove.stripTrailingZeros().toPlainString();
            case "shipping.flatRate" -> cfg.shippingFlatRate.stripTrailingZeros().toPlainString();
            case "shiprocket.pickupLocation" -> cfg.pickupLocation;
            case "shiprocket.pickupPostcode" -> cfg.pickupPostcode;
            case "shiprocket.defaultWeightKg" -> BigDecimal.valueOf(cfg.defaultWeightKg).stripTrailingZeros().toPlainString();
            case "shiprocket.lengthCm" -> String.valueOf(cfg.parcelLengthCm);
            case "shiprocket.breadthCm" -> String.valueOf(cfg.parcelBreadthCm);
            case "shiprocket.heightCm" -> String.valueOf(cfg.parcelHeightCm);
            case "exchange.windowDays" -> String.valueOf(cfg.exchangeWindowDays);
            case "inventory.lowStockThreshold" -> String.valueOf(cfg.lowStockThreshold);
            default -> extra.getOrDefault(key, Settings.defaultFor(key));
        };
    }

    /** Effective value of any known setting. */
    public String get(String key) { return current(key); }

    public boolean flag(String key) { return Boolean.parseBoolean(current(key)); }

    public BigDecimal money(String key) {
        try { return new BigDecimal(current(key)); } catch (RuntimeException e) { return BigDecimal.ZERO; }
    }

    private void apply(String key, String v) {
        try {
            switch (key) {
                case "business.name" -> cfg.businessName = v;
                case "business.email" -> cfg.businessEmail = v;
                case "business.whatsapp" -> cfg.businessWhatsapp = v;
                case "business.address" -> cfg.businessAddress = v;
                case "cod.enabled" -> cfg.codEnabled = Boolean.parseBoolean(v);
                case "cod.maxOrderValue" -> cfg.codMaxOrderValue = new BigDecimal(v);
                case "payment.expiryMinutes" -> cfg.paymentExpiryMinutes = Integer.parseInt(v);
                case "shipping.freeForAll" -> cfg.shippingFreeForAll = Boolean.parseBoolean(v);
                case "shipping.freeAbove" -> cfg.shippingFreeAbove = new BigDecimal(v);
                case "shipping.flatRate" -> cfg.shippingFlatRate = new BigDecimal(v);
                case "shiprocket.pickupLocation" -> cfg.pickupLocation = v;
                case "shiprocket.pickupPostcode" -> cfg.pickupPostcode = v;
                case "shiprocket.defaultWeightKg" -> cfg.defaultWeightKg = Double.parseDouble(v);
                case "shiprocket.lengthCm" -> cfg.parcelLengthCm = Integer.parseInt(v);
                case "shiprocket.breadthCm" -> cfg.parcelBreadthCm = Integer.parseInt(v);
                case "shiprocket.heightCm" -> cfg.parcelHeightCm = Integer.parseInt(v);
                case "exchange.windowDays" -> cfg.exchangeWindowDays = Integer.parseInt(v);
                case "inventory.lowStockThreshold" -> cfg.lowStockThreshold = Integer.parseInt(v);
                default -> {
                    if (Settings.find(key).isPresent()) extra.put(key, v);
                    else log.warn("Ignoring unknown saved setting {}", key);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Ignoring bad saved value for {}: {}", key, e.getMessage());
        }
    }
}
