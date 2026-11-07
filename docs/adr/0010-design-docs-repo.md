# ADR-0010 設計書は専用リポジトリへ自動生成・自動同期する

- 状態: Accepted (2026-11-07, 本人の要望による)

## 決定
設計書（テーブル定義書・ER図・API定義書・画面定義書など）は、専用リポジトリ
`t-nozue-v2/kakeibo-docs`（Private）に置き、手書きせずソースから自動生成する。

- 生成: `tools/design-docs/generate.py`（Python標準ライブラリのみ）が、DDL・Controller・Nuxt画面・
  本体文書（要件定義・ADR・決定事項）を正として Markdown を生成する。
- 同期: 本体 main への push を契機に GitHub Actions（`.github/workflows/design-docs-sync.yml`）が
  再生成し、差分があれば設計書リポジトリへ commit/push する。
- 別リポジトリへの書き込み権限は、設計書リポジトリ専用の Deploy Key（書き込み可）を使う。
  公開鍵は kakeibo-docs の Deploy Key、秘密鍵は kakeibo の Secret `DESIGN_DOCS_DEPLOY_KEY` に置く。
  `GITHUB_TOKEN` は自リポジトリにしか効かないため使えない。PAT より権限範囲が狭いので Deploy Key を選んだ。

## 理由
- 設計書を手で直す方式では本体の変更に追随できず、すぐに陳腐化するため。
- ソースを正とすれば、main の更新 = 設計書の更新が機械的に保証される。

## 影響
- 設計書リポジトリ側は直接編集しない（次回同期で上書きされる）。直したい場合は生成元か
  ジェネレータを直す。
- ジェネレータの解析が追いつかない構文が増えたら、生成物ではなくジェネレータ側を拡張する。
