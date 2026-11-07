# Kakeibo

マネーフォワード風の家計簿Webアプリ。モノレポ構成（UI / API分離）。

- GitHub: `root-admin-jp/kakeibo`（Private）
- アプリ名: Kakeibo / 通貨: JPY / 言語: 日本語
- 決定事項: `docs/decisions.md` / 要件: `docs/requirements/requirements.md` / ADR: `docs/adr/`
- 設計書（自動生成）: 別リポジトリ `root-admin-jp/kakeibo-docs`（テーブル定義書・ER図・API定義書・画面定義書。main 更新のたびに自動同期）

## 技術構成

| 区分 | 技術 |
|---|---|
| API | Kotlin / Spring Boot 3.5.16 / Gradle / Flyway + MyBatis / REST + OpenAPI |
| DB | MySQL 8 |
| Web | Nuxt 4.6.4 / TypeScript / SSR / Tailwind CSS |
| 認証 | メール + パスワード（JWT） |

将来の一般公開を視野に、マルチユーザー前提でユーザー間データを分離している（共有・招待機能はなし）。

## 構成
```
/
├─ apps/
│  ├─ api/            # Kotlin API（詳細は apps/api/README.md）
│  └─ web/            # Nuxt Web（ソースは app/ 配下。詳細は apps/web/README.md）
├─ db/migration/      # Flyway DDL
├─ docs/
│  ├─ adr/
│  ├─ requirements/
│  ├─ decisions.md
│  └─ development-workflow.md   # PR・レビュー運用
├─ infra/docker-compose.yml     # ローカル用 MySQL 8
├─ tools/design-docs/           # 設計書ジェネレータ
├─ .github/workflows/           # CI / E2E / Snyk / 設計書同期
├─ .env.example
└─ README.md
```

## 実装済み（Phase 1 MVP）
ログイン・登録、口座管理、収支 CRUD、カテゴリ管理、月次ダッシュボード、CSV 入出力（汎用 / マネーフォワード形式）、金融機関マスタ（対象 8 機関）。

金融機関連携は Phase 1 では CSV 取り込みとし、将来は機関別マッピング（Phase 2）と公式 API 等への差し替え（`InstitutionConnector`、ADR-0006）を予定。

## テストと品質チェック
- CI（`.github/workflows/ci.yml`）: API 単体・結合テスト 39 件、Web 単体テスト 13 件＋ビルド
- E2E（`e2e.yml`）: Playwright 6 シナリオ（実 MySQL + API + Web で実行）
- 脆弱性: Snyk（`snyk.yml`、high 以上で fail、週次スキャン併用）、Dependabot（週次更新 PR）

## 開発の進め方
`main` への変更は Pull Request 経由とし、レビューは Muse が担当する。流れと観点は `docs/development-workflow.md` を参照。

## ローカル開発
1. `.env.example` をコピーして `.env` を作る（実値はコミットしない。ADR-0005）。
2. `docker compose -f infra/docker-compose.yml up -d` で MySQL 8 を起動。
3. API: `apps/api` で `gradle bootRun`（詳細は `apps/api/README.md`）。
4. Web: `apps/web` で `npm install` 後 `npm run dev`（詳細は `apps/web/README.md`）。

クレデンシャルはコミットしない（ADR-0005）。
