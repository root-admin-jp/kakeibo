# Kakeibo Web

Nuxt 4.6.4 + TypeScript + SSR / Bootstrap 5.3。ソースは Nuxt 4 標準の `app/` 配下（pages / layouts / composables / middleware）。APIとは OpenAPI 経由でのみ連携する (ADR-0004)。

## 画面（5画面）
ログイン / 登録、ダッシュボード（月次集計）、取引、口座、カテゴリ。

## 起動
API が起動している状態で:
```
npm install
npm run dev        # 開発サーバー
npm run build      # 本番ビルド（.output を生成）
npm run preview    # ビルドのプレビュー
```

## テスト
```
npm run test:unit  # Vitest + @nuxt/test-utils（13件、tests/）
npm run test:e2e   # Playwright（8シナリオ、e2e/。実API+MySQLが必要）
```
