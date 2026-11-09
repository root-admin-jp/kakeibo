-- OTP ログインコード (ADR-0014、決定O)
-- コード本体は保存せず SHA-256 ハッシュのみを保持する。
-- display_name は新規登録用（コード発行時に任意で受け取り、検証成功時のアカウント作成に使う）。

CREATE TABLE IF NOT EXISTS login_codes (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email        VARCHAR(255)    NOT NULL,
  code_hash    CHAR(64)        NOT NULL,
  display_name VARCHAR(100)    NULL,
  attempts     INT             NOT NULL DEFAULT 0,
  expires_at   TIMESTAMP       NOT NULL,
  consumed_at  TIMESTAMP       NULL,
  created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_login_codes_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
