# ADR-0003 DBは MySQL 8 + Flyway

- 状態: Accepted (2026-11-07)

## 決定
MySQL 8。スキーマ変更は Flyway マイグレーション (`db/migration/V*.sql`) でのみ行い、DDLをコミットする。

## 理由
本人の指定 (MySQL、DDLコミット可)。手動でDBを変更しない運用にするため。

## 影響
文字コード utf8mb4。ローカルMySQLは docker-compose で用意する。
