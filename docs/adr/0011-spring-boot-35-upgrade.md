# ADR-0011 Spring Boot を 3.5.x へアップグレードする

- 状態: Accepted (2026-11-07, 本人の承認による)

## 決定
API の Spring Boot を 3.3.5 → **3.5.16**（3.5 系最新）へ上げる。併せて互換のため
関連ライブラリも上げる。

- org.springframework.boot: 3.3.5 → 3.5.16（io.spring.dependency-management 1.1.6 → 1.1.7）
- mybatis-spring-boot-starter: 3.0.3 → 3.0.5（3.x 系のまま、Boot 3.5 対応版）
- springdoc-openapi-starter-webmvc-ui: 2.6.0 → 2.8.17（Boot 3.5 / Framework 6.2 対応ライン）
- opencsv: 5.9 → 5.12.0
- testcontainers (test): 1.20.4 → 1.21.3

## 理由
- Snyk 初回スキャン（2026-11-07）で API 側 68 件（CRITICAL 16 / HIGH 52）が検出され、
  大半が Boot 3.3.5 の管理バージョン（Spring Framework 6.1.14、jackson-databind 2.17.2、
  tomcat-embed 10.1.31、logback 1.5.11）に集中していた。
- Boot 4.x は Jackson 3 化など変更幅が大きく、今回は 3.5 系最新への追随で
  既知の脆弱性の大半を解消しつつ、破壊的変更のリスクを抑える。
- Boot 3.5 の BOM で Framework 6.2.x / Jackson 2.19.x / Tomcat 10.1.4x 系 /
  Flyway 11.x / MySQL Connector 9.x 等に一括で上がる。

## 影響
- 検証は全テスト（API単体・結合 39 件）＋ E2E ＋ Snyk 再スキャンで担保する。
- 今後も Dependabot の週次 PR と Snyk の週次スキャンで追随する。
- Boot 4.x への移行は別途判断する（Jackson 3 のグループ ID 変更等を含むため）。

## 追記 (2026-11-07): パッチ版の上書き指定
3.5.16 適用後の再スキャンで 68 件 → 30 件に減ったが、残件のうち Boot の BOM より
新しいパッチが存在するものについては、本人の承認で `dependencyManagement` による
上書き指定を追加した（build.gradle.kts）。

- jackson-bom 2.21.8（BOM ごと差し替え）
- tomcat-embed (core/websocket/el) 10.1.61
- logback (classic/core) 1.5.38
- commons-lang3 3.21.0 / commons-compress 1.28.0
- swagger-core-jakarta 2.2.55

Spring Framework 6.2.19 / Spring Security 6.5.11 は各ライン内の最新が現行版そのもの
であり、上書きでは解消できない（修正は Framework 7 / Security 7、つまり Boot 4 のみ）。
上書きはパッチレベルに限定し、Boot を更新する際に必要性を再評価する。
