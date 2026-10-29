-- Admin panel upgrade: customer support chat and website banners. Existing tables and data are untouched.

CREATE TABLE support_conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  access_token CHAR(32) NOT NULL,
  customer_name VARCHAR(80) NOT NULL,
  customer_email VARCHAR(190) NOT NULL,
  user_id BIGINT NULL,
  order_number VARCHAR(24) NULL,
  status VARCHAR(8) NOT NULL DEFAULT 'OPEN',
  last_message_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sc_last (last_message_at),
  KEY idx_sc_email (customer_email),
  CONSTRAINT fk_sc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT chk_sc_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender VARCHAR(8) NOT NULL,
  staff_email VARCHAR(190) NULL,
  body VARCHAR(2000) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  KEY idx_sm_conv (conversation_id, id),
  KEY idx_sm_unread (sender, read_at),
  CONSTRAINT fk_sm_conv FOREIGN KEY (conversation_id) REFERENCES support_conversations(id) ON DELETE CASCADE,
  CONSTRAINT chk_sm_sender CHECK (sender IN ('CUSTOMER','STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE banners (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  subtitle VARCHAR(200) NULL,
  image_url VARCHAR(300) NULL,
  link_url VARCHAR(300) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_banner_active (active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
