# ADR-0001 モノレポ構成にする

- 状態: Accepted (2026-11-07, 本人指定)

## 決定
UI (Nuxt) と API (Kotlin) を1つのリポジトリ `root-admin-jp/kakeibo` に置く。`apps/api`, `apps/web`, `db/migration`, `docs`, `infra` に分ける。

## 理由
UI/APIの契約変更を1つのPRで完結させるため。本人の指定。

## 影響
CIはパス別に分ける。デプロイはサービスごとに分離できる構成を保つ。
