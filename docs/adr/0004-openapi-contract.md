# ADR-0004 UI/API分離と OpenAPI 契約

- 状態: Accepted (2026-11-07)

## 決定
REST + OpenAPI 3 (springdoc)。Nuxt側は OpenAPI から型/クライアントを生成する。UIからDBへ直接アクセスしない。

## 理由
UI/API分離の指定を生成物で担保するため。

## 影響
APIの破壊的変更は OpenAPI 差分で検出できる。
