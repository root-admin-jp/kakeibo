# E2E確認プラン（ドラフト・未コミット・2026-11-07作成）

## 結果（2026-11-08 更新）
本人が「まずE2E確認から」を選択し、以下まで完了した。
- チェックリスト1・2: Testcontainers の実MySQL 8 で Flyway 適用＋MyBatis 全Mapperを検証済み（Mapper結合テスト7件。当時の実バグ `budgets.year_month` のキーワード衝突もここで発見・修正）
- チェックリスト3: Playwright E2E（CI上で実MySQL＋実API＋実Web）で 登録→ログイン→口座→取引→集計→再ログイン を検証済み。2026-11-08 に CSV取り込み・CSVエクスポートの画面操作も追加し **8シナリオ**全通過（PR #24）
- チェックリスト5: Gradle Wrapper なしでも CI は継続動作中
- チェックリスト4（docker-compose の initdb.d と Flyway の競合）: **未検証のまま**。このVMには Docker がないため実機確認ができない。静的には `baseline-on-migrate: true` により初回起動時に Flyway がベースライン化して二重適用にならない設計。ユーザー環境（Docker）での最終確認が残る
- 以降の記述は 2026-11-07 20時台時点の古い状態メモ（テスト本数などは現状と異なる）

目的: Kakeibo Phase 1 MVPの「実MySQLに対する起動・E2E確認」を、ユーザーの選択（E2E確認 vs Phase 2）を待たずに進められる形にしておくための下書き。実装ではなく計画＋CIドラフト。
前提: MyBatis移行（コミット 4bdb47b＋8993487、CIはAPI・Webとも成功）は済んでいるが、MyBatisの実MySQLに対するマッピングは未検証のまま。

## 今の状態（2026-11-07 20時台に確認）

- テストは `JwtServiceTest` 1本のみ。Mapper・DBまわりのテストはゼロ。
- CI（`.github/workflows/ci.yml`）のAPIジョブは `gradle test` のみで、MySQLサービスなし。DBなしのコンパイル＋単体テストまで。
- `apps/api/build.gradle.kts` に Testcontainers等のDBテスト用依存なし。
- `infra/docker-compose.yml` は `db`（mysql:8.0）のみ。API・Webサービスは未追加。
- このVMには Java・Docker・MySQLが入っていないため、ローカルでのE2E実行はここではできない。
- `application.yml` のFlyway設定は `locations: filesystem:../../db/migration`（`apps/api`起点の相対パス）。CIのAPIジョブは `working-directory: apps/api` なので解決するが、実行ディレクトリ依存である点は確認項目。
- `docker-compose.yml` は `../db/migration` をMySQLのinitdb.dにマウントしている（初回起動時にV1が直接実行される）。Flywayは `baseline-on-migrate: true` なので、初回起動時の二重適用にならないかはE2Eで確認する項目。

## 確認したいこと（チェックリスト）

1. Flyway V1（`db/migration/V1__init.sql`）がMySQL 8にクリーン適用される
2. Spring Bootが実MySQL＋MyBatis 7Mapper（User/Household/Account/Category/Transaction/Institution/ImportJob）で起動する
3. 一通りの操作: 登録（テナント＋初期カテゴリ自動作成）→ ログイン（JWT）→ 口座作成 → 取引作成 → 月次集計 → CSV取り込み（MF形式）→ CSVエクスポート
4. docker-compose初回起動時、initdb.dのV1とFlywayが競合しない
5. Gradle Wrapper未生成のままCIが動き続けるか（現状はCIがgradleを直接使って成功）

## 進め方の案

### A案（推奨）: CIにMySQLサービスを足して検証する
- VMにJava・Dockerがないため、ローカル再現よりCIの方が確実。
- APIジョブに `services: mysql:8.0` を追加し、`DB_HOST: 127.0.0.1` 等の環境変数で接続して `gradle test` を実行する。
- DBテストは最初は `@SpringBootTest` 1本（起動＋Flyway適用＋登録→取引→集計の通し）からで十分。Testcontainersは不要（GitHub Actionsのサービスコンテナで足りる）。
- ドラフト（`ci.yml` のapiジョブへの追加イメージ）:

```yaml
  api:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: kakeibo
          MYSQL_USER: kakeibo
          MYSQL_PASSWORD: kakeibo
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping -h localhost" --health-interval=5s --health-timeout=5s --health-retries=10
    env:
      DB_HOST: 127.0.0.1
      DB_PORT: 3306
      DB_NAME: kakeibo
      DB_USER: kakeibo
      DB_PASSWORD: kakeibo
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - name: API test with MySQL (Gradle)
        working-directory: apps/api
        run: gradle test --console=plain
```

- 注意: 上記はドラフトで、実際にCIで通すにはDBテスト本体（`@SpringBootTest`）の追加と、Flywayの相対パスがCI環境で解決することの確認が必要。

### B案: ユーザーの環境でdocker-composeを使って確認する
- `docker-compose.yml` にAPI・Webサービスを追加し、ユーザーのPCで `docker compose up` してブラウザから一通り操作する。
- 画面側の確認まで含められるのが利点。ただしユーザーの手間がかかり、Dockerの有無も未確認。

## ユーザーへの確認事項（決めてもらうこと）
1. A案（CIでMySQL検証）から進めてよいか。進める場合、DBテスト本体の追加とCI変更まで一気にやるか、まずCIのMySQLサービス追加だけか。
2. B案（ローカルdocker-composeでの画面確認）もやるか。やる場合、ユーザーのPCにDockerがあるか教えてもらう。
3. Phase 2（機関別CSV）の下調べは別途済み（`docs/phase2-csv-survey.md`）。E2EとPhase 2のどちらを先に進めるかは、そちらの結果と合わせて選べる状態。

## 調査日時・範囲
- 調査日: 2026-11-07（日本時間20時台）
- 方法: リポジトリ内のファイル確認のみ。CIの実行やDBの起動はしていない。
- 未検証: 上記チェックリストの全項目。特にMyBatisの実MySQLマッピングとFlywayの相対パス解決は、実際に動かすまで確定しない。
