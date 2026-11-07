# Kakeibo

マネーフォワード風の家計簿Webアプリ。モノレポ構成（UI / API分離）。

- GitHub: `root-admin-jp/kakeibo`（Private。初回プッシュは認証後に実施）
- アプリ名: Kakeibo / 通貨: JPY / 言語: 日本語
- 決定事項: `docs/decisions.md` / 要件: `docs/requirements/requirements.md` / ADR: `docs/adr/`

## 構成
```
/
├─ apps/
│  ├─ api/        # Kotlin / Spring Boot 3 / Gradle / Flyway + MyBatis
│  └─ web/        # Nuxt 3 / TypeScript / Tailwind
├─ db/migration/  # Flyway DDL (V1__init.sql)
├─ docs/
│  ├─ adr/
│  ├─ requirements/
│  └─ decisions.md
├─ infra/docker-compose.yml
├─ .env.example
└─ README.md
```

## ローカル開発（予定）
1. `.env.example` をコピーして `.env` を作る（実値はコミットしない）。
2. `docker compose -f infra/docker-compose.yml up -d` で MySQL 8 を起動。
3. API / Web ともに Phase 1 実装済み。起動手順は各アプリの README を参照。ビルド検証は GitHub Actions (CI) で行う。

クレデンシャルはコミットしない（ADR-0005）。
