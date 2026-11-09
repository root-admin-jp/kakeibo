-- パスワードレス移行 (ADR-0014、決定O): パスワード認証を撤去するため users.password_hash を削除する。
-- 既存ユーザーは同じメールアドレスで OTP ログインできるため、移行による締め出しは発生しない。

ALTER TABLE users DROP COLUMN password_hash;
