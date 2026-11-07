# Kakeibo API のコンテナイメージ（ビルドコンテキストはリポジトリルート）
# リリース時に .github/workflows/release-images.yml が GHCR へ発行する。
FROM gradle:jdk21 AS build
WORKDIR /src
COPY apps/api/ apps/api/
WORKDIR /src/apps/api
RUN gradle bootJar -x test --no-daemon && cp build/libs/*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source="https://github.com/t-nozue-v2/kakeibo"
WORKDIR /app
COPY --from=build /tmp/app.jar app.jar
# Flyway の DDL は jar の外に置き、場所を環境変数で上書きする（application.yml の相対パスは開発用）
COPY db/migration /app/migrations
ENV SPRING_FLYWAY_LOCATIONS=filesystem:/app/migrations
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
