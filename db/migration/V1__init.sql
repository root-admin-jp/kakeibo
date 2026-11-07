-- DDL v0.2: Kakeibo 初期スキーマ (Flyway V1)
-- 対応: docs/requirements/requirements.md v0.2, ADR-0003, ADR-0007
-- 方針: 1テナント(households)=1オーナーユーザー専用。共有/招待なし。
-- 金額は JPY の整数。金融機関の認証情報は保存しない。

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255)    NOT NULL,
  password_hash VARCHAR(255)    NOT NULL,
  display_name  VARCHAR(100)    NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS households (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT UNSIGNED NOT NULL,
  name       VARCHAR(100)    NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_households_owner (owner_user_id),
  CONSTRAINT fk_households_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS financial_institutions (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code       VARCHAR(50)     NOT NULL,
  name       VARCHAR(100)    NOT NULL,
  kind       ENUM('bank','credit_union','credit_card','e_money','securities','other') NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_institutions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- B1 確定リスト (Phase 1 マスタ)
INSERT INTO financial_institutions (code, name, kind) VALUES
  ('aeon_bank', 'イオン銀行', 'bank'),
  ('mizuho_bank', 'みずほ銀行', 'bank'),
  ('paypay_bank', 'PayPay銀行', 'bank'),
  ('shizuoka_bank', '静岡銀行', 'bank'),
  ('hamamatsu_iwata_shinkin', '浜松磐田信用金庫', 'credit_union'),
  ('sbi_securities', 'SBI証券', 'securities'),
  ('mobile_waon', 'モバイルWAON', 'e_money'),
  ('mobile_suica', 'モバイルSuica', 'e_money')
ON DUPLICATE KEY UPDATE name = VALUES(name), kind = VALUES(kind);

CREATE TABLE IF NOT EXISTS accounts (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id   BIGINT UNSIGNED NOT NULL,
  institution_id BIGINT UNSIGNED NULL,
  name           VARCHAR(100)    NOT NULL,
  kind           ENUM('cash','bank','credit_card','e_money','securities','other') NOT NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_accounts_household   FOREIGN KEY (household_id)   REFERENCES households (id),
  CONSTRAINT fk_accounts_institution FOREIGN KEY (institution_id) REFERENCES financial_institutions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS categories (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  parent_id    BIGINT UNSIGNED NULL,
  name         VARCHAR(100)    NOT NULL,
  sort_order   INT NOT NULL DEFAULT 0,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_categories_household FOREIGN KEY (household_id) REFERENCES households (id),
  CONSTRAINT fk_categories_parent    FOREIGN KEY (parent_id)    REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS transactions (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id     BIGINT UNSIGNED NOT NULL,
  account_id       BIGINT UNSIGNED NOT NULL,
  to_account_id    BIGINT UNSIGNED NULL,
  category_id      BIGINT UNSIGNED NULL,
  type             ENUM('income','expense','transfer') NOT NULL,
  amount           BIGINT NOT NULL,
  transacted_on    DATE NOT NULL,
  memo             VARCHAR(500) NULL,
  deleted_at       TIMESTAMP NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_transactions_household_date (household_id, transacted_on),
  INDEX idx_transactions_account (account_id),
  CONSTRAINT fk_transactions_household   FOREIGN KEY (household_id)  REFERENCES households (id),
  CONSTRAINT fk_transactions_account     FOREIGN KEY (account_id)    REFERENCES accounts (id),
  CONSTRAINT fk_transactions_to_account  FOREIGN KEY (to_account_id) REFERENCES accounts (id),
  CONSTRAINT fk_transactions_category    FOREIGN KEY (category_id)   REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS budgets (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  category_id  BIGINT UNSIGNED NOT NULL,
  year_month   CHAR(7) NOT NULL,
  amount       BIGINT NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_budgets (household_id, category_id, year_month),
  CONSTRAINT fk_budgets_household FOREIGN KEY (household_id) REFERENCES households (id),
  CONSTRAINT fk_budgets_category  FOREIGN KEY (category_id)  REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS import_jobs (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  account_id   BIGINT UNSIGNED NULL,
  institution_id BIGINT UNSIGNED NULL,
  source       ENUM('generic_csv','institution_csv','manual') NOT NULL DEFAULT 'generic_csv',
  status       ENUM('pending','done','failed') NOT NULL DEFAULT 'pending',
  imported_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(1000) NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_import_jobs_household FOREIGN KEY (household_id) REFERENCES households (id),
  CONSTRAINT fk_import_jobs_account   FOREIGN KEY (account_id)   REFERENCES accounts (id),
  CONSTRAINT fk_import_jobs_institution FOREIGN KEY (institution_id) REFERENCES financial_institutions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS institution_connections (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id   BIGINT UNSIGNED NOT NULL,
  institution_id BIGINT UNSIGNED NOT NULL,
  connector_type VARCHAR(100) NOT NULL,
  external_ref   VARCHAR(255) NULL,
  status         ENUM('active','revoked') NOT NULL DEFAULT 'active',
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_ic_household   FOREIGN KEY (household_id)   REFERENCES households (id),
  CONSTRAINT fk_ic_institution FOREIGN KEY (institution_id) REFERENCES financial_institutions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
