# ADR-0012 Nuxt を 4.x へ移行する

- 状態: Accepted (2026-11-07, 本人の承認による)

## 決定
Web の Nuxt を 3.21.11 → **4.6.4**（4 系最新）へ上げる。併せて関連ツールを上げる。

- nuxt: 3.21.11 → 4.6.4
- vue-router: 4.4.x → 5.4.x（Nuxt 4 が要求するライン）
- @nuxt/test-utils: 3.23.0 → 4.3.4
- vitest: 3.x → 5.1.0
- @nuxtjs/tailwindcss は 6.14.0 のまま（6 系で Nuxt 4 対応済み）
- ディレクトリ構成を Nuxt 4 標準の `app/` 配下へ移動する
  （`app.vue` / `pages/` / `layouts/` / `composables/` / `middleware/` → `apps/web/app/` 配下）。
  単体テストの import パスと設計書ジェネレータ（`tools/design-docs/generate.py`）の参照パスも追随させる。
- `nuxt.config.ts` の compatibilityDate を 2024-11-01 → 2025-07-15 に更新する。

## 理由
- Nuxt 3 は 2026-07-31 に EOL（公式ロードマップ）で、セキュリティ修正が来ないラインになった。
- Snyk で Web 側 HIGH 3 件（braces / ioredis の Uncontrolled Recursion、@nuxt/vite-builder の
  ファイル公開）が nuxt 3.21.11 の推移依存に残り、3 系では解消できない。
- 移行はブランチで試し、単体テスト・ビルド・E2E が全部通ることを確認してから main へマージする進め方を取る。

## 影響
- 画面・API 連携の挙動は変更しない（構成とツールチェーンのみの変更）。
- 設計書の画面定義書に記載されるファイルパスが `apps/web/app/pages/...` になる。
- API 側の Snyk 残り 9 件（Spring Framework / Security 由来）は Spring Boot 4 移行の話であり、本決定の対象外。
