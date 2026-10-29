-- Admin-editable, NON-secret store settings. Secrets (Razorpay, Shiprocket, SMTP, JWT) stay in environment variables.
CREATE TABLE app_settings (
  setting_key   VARCHAR(80)  NOT NULL PRIMARY KEY,
  setting_value VARCHAR(500) NOT NULL,
  updated_by    VARCHAR(190) NULL,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
