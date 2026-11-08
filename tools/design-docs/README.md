# 設計書ジェネレータ

`generate.py` は、ソースを正として設計書一式を Markdown 生成する（Python 3 標準ライブラリのみ）。

| 生成物 | 生成元 |
|---|---|
| `database/table-definition.md` テーブル定義書 | `db/migration/V1__init.sql` |
| `database/er-diagram.md` ER図 (Mermaid) | DDL の外部キー制約 |
| `api/api-definition.md` API定義書 | `apps/api` の Controller / DTO |
| `screens/screen-definition.md` 画面定義書 + 画面遷移図 | `apps/web/pages/*.vue` |
| `requirements/` `adr/` `decisions.md` | 本体 `docs/` のスナップショット複写 |
| `README.md` | 上記インデックス + システム概要 + ADR一覧 |

## 自動同期の仕組み

```
kakeibo main へ push
  └─ GitHub Actions (.github/workflows/design-docs-sync.yml)
       ├─ generate.py で設計書を生成
       ├─ 設計書リポジトリ root-admin-jp/kakeibo-docs を Deploy Key で checkout
       │    （秘密鍵は kakeibo リポジトリの Secret: DESIGN_DOCS_DEPLOY_KEY、
       │      公開鍵は kakeibo-docs の Deploy Key (書き込み可) に登録）
       └─ 差分があれば commit + push（差分なしなら何もしない）
```

- `GITHUB_TOKEN` は自リポジトリにしか効かないため、別リポジトリへの push には Deploy Key を使う。
  PAT と違い、この1リポジトリ専用・失効も単独でできる。
- 設計書リポジトリ側は直接編集しない（次回同期で上書きされる）。

## ローカル実行

```bash
python3 tools/design-docs/generate.py --source . --out /tmp/kakeibo-docs-preview
```
