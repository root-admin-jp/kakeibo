# Kakeibo Web のコンテナイメージ（ビルドコンテキストはリポジトリルート）
# リリース時に .github/workflows/release-images.yml が GHCR へ発行する。
FROM node:22 AS build
COPY apps/web/ /src/apps/web/
WORKDIR /src/apps/web
RUN npm ci && npm run build

FROM node:22-slim
LABEL org.opencontainers.image.source="https://github.com/t-nozue-v2/kakeibo"
WORKDIR /app
COPY --from=build /src/apps/web/.output ./.output
# API の接続先は実行時に NUXT_PUBLIC_API_BASE で差し替え可能（デフォルトは http://localhost:8080）
ENV HOST=0.0.0.0 PORT=3000
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
