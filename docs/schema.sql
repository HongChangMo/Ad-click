CREATE TABLE IF NOT EXISTS ads (
  id BIGINT NOT NULL AUTO_INCREMENT,
  advertiser_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ad_balances (
  ad_id BIGINT NOT NULL,
  balance DECIMAL(15, 2) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (ad_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS balance_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ad_id BIGINT NOT NULL,
  amount DECIMAL(15, 2) NOT NULL,
  type VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_balance_transactions_ad_id (ad_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS click_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ad_id BIGINT NOT NULL,
  ip_address VARCHAR(45) NOT NULL,
  anonymous_id VARCHAR(64),
  clicked_at DATETIME(6) NOT NULL,
  is_valid BIT NOT NULL,
  invalid_reason VARCHAR(30),
  PRIMARY KEY (id),
  INDEX idx_click_abuse (ad_id, ip_address, clicked_at),
  INDEX idx_click_stats (ad_id, clicked_at, is_valid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS click_event_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  topic VARCHAR(100) NOT NULL,
  message_key VARCHAR(100) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INT NOT NULL,
  last_error VARCHAR(1000),
  created_at DATETIME(6) NOT NULL,
  published_at DATETIME(6),
  PRIMARY KEY (id),
  INDEX idx_click_event_outbox_status_created (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS processed_click_events (
  click_event_id BIGINT NOT NULL,
  processed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (click_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS click_daily_stats (
  ad_id BIGINT NOT NULL,
  stats_date DATE NOT NULL,
  valid_count BIGINT NOT NULL,
  invalid_count BIGINT NOT NULL,
  PRIMARY KEY (ad_id, stats_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
