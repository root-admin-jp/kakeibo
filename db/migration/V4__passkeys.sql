-- パスキー (WebAuthn) の資格情報とチャレンジ (ADR-0014、決定O)
-- 公開鍵 (COSE) のみを保存し、秘密鍵は利用者の認証器から出ない。

CREATE TABLE IF NOT EXISTS passkey_credentials (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  credential_id VARCHAR(255)    NOT NULL,
  public_key_cose TEXT          NOT NULL,
  sign_count    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  name          VARCHAR(100)    NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at  TIMESTAMP NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_passkey_credential_id (credential_id),
  KEY idx_passkey_user (user_id),
  CONSTRAINT fk_passkey_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 登録・ログインの検証用チャレンジ（ワンタイム、5分で失効。検証時に削除する）
CREATE TABLE IF NOT EXISTS webauthn_challenges (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  challenge    VARCHAR(255)    NOT NULL,
  user_id      BIGINT UNSIGNED NULL,
  kind         VARCHAR(20)     NOT NULL,
  request_json TEXT            NOT NULL,
  expires_at   TIMESTAMP       NOT NULL,
  created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_webauthn_challenge (challenge),
  KEY idx_webauthn_challenge_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
