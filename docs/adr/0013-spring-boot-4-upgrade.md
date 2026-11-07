# ADR-0013 Spring Boot を 4.1.x へアップグレードする

- 状態: Accepted (2026-11-07, 本人の指示による)

## 決定
API の Spring Boot を 3.5.16 → **4.1.2**（4 系安定版の最新）へ上げる。併せて関連ライブラリを Boot 4 系に揃える。

- org.springframework.boot: 3.5.16 → 4.1.2（Spring Framework 7 / Spring Security 7 / Jackson 3 / Tomcat 11）
- kotlin plugin.spring: 2.0.21 → 2.4.21（jvm プラグインと版を統一）
- spring-boot-starter-web → **spring-boot-starter-webmvc**（Boot 4 での改名）
- mybatis-spring-boot-starter: 3.0.5 → 4.1.0
- springdoc-openapi-starter-webmvc-ui: 2.8.17 → 3.2.0
- jackson-module-kotlin: `com.fasterxml.jackson.module` → **`tools.jackson.module`**（Jackson 3 の座標）
- jjwt: api / impl / jackson を 0.13.0 に統一（従来は 0.12.6 と混在）
- testcontainers: mysql / junit-jupiter を 1.21.4 に統一
- ADR-0011 追記の暫定パッチ上書き（jackson-bom 2.21.8、tomcat 10.1.61、logback 1.5.38、
  commons-lang3、commons-compress、swagger-core 2.2.55）は **全て撤去**し、Boot 4 の BOM に委ねる

## 理由
- Snyk で API 側に残っていた 9 件（CRITICAL 1 / HIGH 8）は全て Spring Framework 6.2.19 /
  Spring Security 6.5.11 由来で、各ライン内では修正不能であり、修正は Framework 7 /
  Security 7 = Spring Boot 4 でのみ可能だった（ADR-0011 時点で判明、本人の指示で移行を決定）。
- Boot 4.0 は Jackson 3 化など変更幅が大きいため ADR-0011 では見送ったが、今回は
  残件解消のため移行する。アプリケーションコードは Jackson を直接使っておらず
  （Spring MVC と jjwt 経由のみ）、Security 設定もモダンなラムダ DSL のため、
  追随はビルド定義中心で済む見込みとした。

## 影響
- 検証は全テスト（API単体・結合 39 件）＋ E2E ＋ Snyk 再スキャンで担保する。
- jjwt-jackson 0.13.0 は Jackson 2 (com.fasterxml) 系に依存する。Jackson 3 (tools.jackson) とは
  パッケージが分かれるため共存可能だが、JWT の往復テストで動作を検証する。
- Boot 3.5 系へのダウングレードは行わない。

## 追記 (2026-11-07): Jackson の CVE-2026-110879 への対応
移行後の Snyk 再スキャンで、元の 9 件（Framework / Security 由来）は全て解消した。
一方で 2026-10-28 公開の CVE-2026-110879（Jackson の多態デシリアライゼーション検証
バイパス、CRITICAL）が新規に検出された。対象は Boot 4.1.2 管理版の
tools.jackson.core:jackson-databind 3.1.7 と、springdoc 3.2.0 経由の
com.fasterxml.jackson.core:jackson-databind 2.21.7。
当アプリはデフォルトタイピング等を使わないため悪用条件は満たさないが、修正版が
存在するため `dependencyManagement` で jackson-bom を 3.1.8 / 2.21.8 へ引き上げて対応する。
Boot が管理版を 3.1.8 以降へ更新したら、この上書きは撤去すること。
