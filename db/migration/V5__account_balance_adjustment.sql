-- DDL v0.3: 口座の「現在の残高」設定（ADR-0015 / 決定P）
-- 1) 取引種別に 'adjustment'（残高調整）を追加する。金額は符号付きで、差額そのものを保持する。
-- 2) accounts.balance_offset: 「取引に記録しない」を選んだ残高調整の累積差額。
--    残高 = 取引の合計 + balance_offset で算出する。

ALTER TABLE transactions MODIFY COLUMN type ENUM('income','expense','transfer','adjustment') NOT NULL;

ALTER TABLE accounts ADD COLUMN balance_offset BIGINT NOT NULL DEFAULT 0;
