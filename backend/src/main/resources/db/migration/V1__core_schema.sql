-- TrendsFit core schema (MySQL 8 / MariaDB 10.6+ compatible). Money is DECIMAL(10,2) in INR.

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL,
  phone VARCHAR(20) NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',
  email_verified TINYINT(1) NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  marketing_consent TINYINT(1) NOT NULL DEFAULT 0,
  failed_logins INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE verification_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  type VARCHAR(20) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_vt_hash (token_hash),
  KEY idx_vt_user (user_id),
  CONSTRAINT fk_vt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE addresses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  label VARCHAR(20) NOT NULL DEFAULT 'HOME',
  full_name VARCHAR(120) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  line1 VARCHAR(200) NOT NULL,
  line2 VARCHAR(200) NULL,
  city VARCHAR(80) NOT NULL,
  state VARCHAR(80) NOT NULL,
  pincode CHAR(6) NOT NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  KEY idx_addr_user (user_id),
  CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(80) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(300) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_cat_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(160) NOT NULL,
  name VARCHAR(160) NOT NULL,
  sku VARCHAR(60) NOT NULL,
  category_id BIGINT NOT NULL,
  description TEXT NULL,
  fit VARCHAR(40) NULL,
  material VARCHAR(120) NULL,
  care TEXT NULL,
  size_chart TEXT NULL,
  video_url VARCHAR(400) NULL,
  price DECIMAL(10,2) NOT NULL,
  sale_price DECIMAL(10,2) NULL,
  seo_title VARCHAR(160) NULL,
  seo_description VARCHAR(300) NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
  is_featured TINYINT(1) NOT NULL DEFAULT 0,
  is_new_arrival TINYINT(1) NOT NULL DEFAULT 0,
  is_bestseller TINYINT(1) NOT NULL DEFAULT 0,
  is_demo TINYINT(1) NOT NULL DEFAULT 0,
  rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0,
  rating_count INT NOT NULL DEFAULT 0,
  sold_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_prod_slug (slug),
  UNIQUE KEY uq_prod_sku (sku),
  KEY idx_prod_cat_status (category_id, status),
  KEY idx_prod_status_created (status, created_at),
  KEY idx_prod_price (price),
  CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT chk_prod_price CHECK (price >= 0 AND (sale_price IS NULL OR (sale_price >= 0 AND sale_price <= price)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  url VARCHAR(400) NOT NULL,
  alt VARCHAR(200) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  KEY idx_pi_product (product_id),
  CONSTRAINT fk_pi_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_variants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  sku VARCHAR(80) NOT NULL,
  size VARCHAR(10) NOT NULL,
  color VARCHAR(40) NOT NULL,
  color_hex CHAR(7) NULL,
  stock INT NOT NULL DEFAULT 0,
  price_override DECIMAL(10,2) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uq_var_sku (sku),
  UNIQUE KEY uq_var_combo (product_id, size, color),
  KEY idx_var_product (product_id),
  CONSTRAINT fk_var_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  CONSTRAINT chk_var_stock CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_movements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  variant_id BIGINT NOT NULL,
  delta INT NOT NULL,
  reason VARCHAR(40) NOT NULL,
  ref_type VARCHAR(20) NULL,
  ref_id BIGINT NULL,
  actor_email VARCHAR(190) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sm_variant (variant_id, created_at),
  CONSTRAINT fk_sm_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(40) NOT NULL,
  type VARCHAR(10) NOT NULL,
  value DECIMAL(10,2) NOT NULL,
  min_order DECIMAL(10,2) NOT NULL DEFAULT 0,
  max_discount DECIMAL(10,2) NULL,
  starts_at DATETIME NULL,
  expires_at DATETIME NULL,
  max_uses INT NULL,
  per_user_limit INT NULL,
  used_count INT NOT NULL DEFAULT 0,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_coupon_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_number VARCHAR(24) NOT NULL,
  user_id BIGINT NULL,
  customer_name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  ship_line1 VARCHAR(200) NOT NULL,
  ship_line2 VARCHAR(200) NULL,
  ship_city VARCHAR(80) NOT NULL,
  ship_state VARCHAR(80) NOT NULL,
  ship_pincode CHAR(6) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  payment_method VARCHAR(10) NOT NULL,
  payment_status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  subtotal DECIMAL(10,2) NOT NULL,
  discount DECIMAL(10,2) NOT NULL DEFAULT 0,
  shipping DECIMAL(10,2) NOT NULL DEFAULT 0,
  total DECIMAL(10,2) NOT NULL,
  coupon_code VARCHAR(40) NULL,
  idempotency_key VARCHAR(64) NULL,
  access_token CHAR(32) NOT NULL,
  delivered_at DATETIME NULL,
  stock_held TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_order_number (order_number),
  UNIQUE KEY uq_order_idem (idempotency_key),
  KEY idx_order_user (user_id, created_at),
  KEY idx_order_status (status, created_at),
  KEY idx_order_email (email),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  sku VARCHAR(80) NOT NULL,
  size VARCHAR(10) NOT NULL,
  color VARCHAR(40) NOT NULL,
  image_url VARCHAR(400) NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  qty INT NOT NULL,
  line_total DECIMAL(10,2) NOT NULL,
  KEY idx_oi_order (order_id),
  KEY idx_oi_product (product_id),
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT chk_oi_qty CHECK (qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  note VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_osh_order (order_id, created_at),
  CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupon_redemptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coupon_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  email VARCHAR(190) NOT NULL,
  order_id BIGINT NOT NULL,
  discount DECIMAL(10,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cr_order (order_id),
  KEY idx_cr_coupon_email (coupon_id, email),
  CONSTRAINT fk_cr_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
  CONSTRAINT fk_cr_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  provider VARCHAR(12) NOT NULL DEFAULT 'RAZORPAY',
  provider_order_id VARCHAR(64) NOT NULL,
  provider_payment_id VARCHAR(64) NULL,
  amount DECIMAL(10,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(12) NOT NULL DEFAULT 'CREATED',
  failure_reason VARCHAR(200) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_pay_provider_order (provider_order_id),
  UNIQUE KEY uq_pay_provider_payment (provider_payment_id),
  KEY idx_pay_order (order_id),
  CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE webhook_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source VARCHAR(12) NOT NULL,
  event_id VARCHAR(120) NOT NULL,
  event_type VARCHAR(60) NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_we_event (source, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shipments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'FORWARD',
  exchange_id BIGINT NULL,
  sr_order_id VARCHAR(40) NULL,
  sr_shipment_id VARCHAR(40) NULL,
  awb VARCHAR(60) NULL,
  courier VARCHAR(80) NULL,
  label_url VARCHAR(500) NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
  status_text VARCHAR(200) NULL,
  tracking_url VARCHAR(500) NULL,
  estimated_delivery DATE NULL,
  last_event_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ship_order (order_id),
  UNIQUE KEY uq_ship_awb (awb),
  CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoices (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  invoice_number VARCHAR(30) NOT NULL,
  issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_inv_order (order_id),
  UNIQUE KEY uq_inv_number (invoice_number),
  CONSTRAINT fk_inv_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exchanges (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  new_variant_id BIGINT NOT NULL,
  reason VARCHAR(60) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  admin_note VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ex_order (order_id),
  KEY idx_ex_status (status, created_at),
  CONSTRAINT fk_ex_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exchange_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exchange_id BIGINT NOT NULL,
  url VARCHAR(400) NOT NULL,
  CONSTRAINT fk_exi_ex FOREIGN KEY (exchange_id) REFERENCES exchanges(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,
  title VARCHAR(120) NULL,
  body VARCHAR(2000) NULL,
  verified_purchase TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_review_user_product (user_id, product_id),
  KEY idx_rev_product (product_id, status),
  CONSTRAINT fk_rev_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  CONSTRAINT fk_rev_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_rev_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wishlist_items (
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, product_id),
  CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_wl_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE newsletter_subscribers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_news_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_email VARCHAR(190) NULL,
  action VARCHAR(60) NOT NULL,
  entity VARCHAR(40) NULL,
  entity_id VARCHAR(40) NULL,
  detail VARCHAR(500) NULL,
  ip VARCHAR(45) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_created (created_at),
  KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO categories (slug, name, description, sort_order) VALUES
 ('jeans', 'Jeans', 'Slim, straight and relaxed fits.', 1),
 ('t-shirts', 'T-Shirts', 'Everyday tees in clean fits.', 2),
 ('shirts', 'Shirts', 'Casual and smart shirts.', 3);
