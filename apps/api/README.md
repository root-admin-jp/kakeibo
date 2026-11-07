# Kakeibo API

Kotlin + Spring Boot 3.5.16 (ADR-0011) / MySQL 8 / Flyway + MyBatis (ADR-0009、SQLは `src/main/resources/mapper/*.xml`) / REST + OpenAPI。

テストは単体・結合 39 件（Controller 単体、JWT、Testcontainers の実 MySQL による Mapper 結合）。CI (GitHub Actions) で実行する。

## 実装済み (Phase 1)
- 認証: POST /api/auth/register（ユーザー+テナント+初期カテゴリ作成）, POST /api/auth/login（JWT発行）
- 口座: GET/POST/PUT/DELETE /api/accounts（残高は取引から算出）
- カテゴリ: GET/POST/DELETE /api/categories
- 取引: GET/POST/PUT/DELETE /api/transactions（月別・口座別・カテゴリ別フィルタ、論理削除）
- 月次集計: GET /api/summary/monthly?month=YYYY-MM
- CSV: GET /api/transactions/export.csv, POST /api/transactions/import（汎用/MF形式）
- 金融機関マスタ: GET /api/institutions

## 起動
MySQL 8 を docker-compose で起動し、環境変数 (DB_*, JWT_SECRET) を設定してから:
```
gradle bootRun
```
Gradle Wrapper は未生成（生成環境の都合）。通常環境では `gradle wrapper --gradle-version 8.10.2` で生成すること。CI (GitHub Actions) が Gradle ビルドの検証を行う。
