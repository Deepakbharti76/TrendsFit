package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text mail through SMTP when trendsfit.mail.enabled=true and SMTP is configured.
 * Otherwise nothing is sent. Links are only written to the log when trendsfit.dev-log-links=true (development only).
 */
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final AppConfig cfg;
    private final ObjectProvider<JavaMailSender> sender;

    public EmailService(AppConfig cfg, ObjectProvider<JavaMailSender> sender) {
        this.cfg = cfg;
        this.sender = sender;
    }

    @Async
    public void send(String to, String subject, String body) {
        try {
            JavaMailSender s = sender.getIfAvailable();
            if (!cfg.mailEnabled || s == null) {
                if (cfg.devLogLinks) log.info("[DEV MAIL not sent] to={} subject={}\n{}", to, subject, body);
                else log.info("Mail not sent (mail disabled): to={} subject={}", to, subject);
                return;
            }
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(cfg.mailFrom);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            s.send(m);
        } catch (RuntimeException e) {
            log.warn("Mail to {} failed: {}", to, e.getClass().getSimpleName());
        }
    }

    @Async
    public void sendVerification(String to, String name, String token) {
        send(to, "Verify your TrendsFit email",
            "Hi " + name + ",\n\nPlease verify your email: " + cfg.frontendUrl + "/verify-email?token=" + token
            + "\n\nIf you did not create this account, ignore this message.\n\n" + cfg.businessName);
    }

    @Async
    public void sendReset(String to, String token) {
        send(to, "Reset your TrendsFit password",
            "We received a request to reset your password.\n\nReset link (valid for 1 hour): " + cfg.frontendUrl
            + "/reset-password?token=" + token + "\n\nIf this was not you, ignore this message.\n\n" + cfg.businessName);
    }

    @Async
    public void sendOrderConfirmation(String to, String orderNumber, String total, String token) {
        send(to, "Your TrendsFit order " + orderNumber,
            "Thank you for your order " + orderNumber + " (total " + total + ").\n\nTrack it here: " + cfg.frontendUrl
            + "/order/" + orderNumber + "?token=" + token + "\n\n" + cfg.businessName);
    }

    @Async
    public void sendStatus(String to, String orderNumber, String statusLabel, String token) {
        send(to, "Order " + orderNumber + ": " + statusLabel,
            "Your order " + orderNumber + " is now: " + statusLabel + ".\n\nDetails: " + cfg.frontendUrl
            + "/order/" + orderNumber + "?token=" + token + "\n\n" + cfg.businessName);
    }
}
