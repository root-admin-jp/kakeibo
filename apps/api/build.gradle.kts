plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "jp.kakeibo"
version = "0.0.1-SNAPSHOT"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }

// Boot 3.5 時代の Snyk 暫定パッチ上書き (ADR-0011 追記) は Boot 4.1.2 への移行 (ADR-0013) で
// 不要になったため撤去した。Boot 4 の BOM が管理する版（Framework 7 / Security 7 /
// Jackson 3 / Tomcat 11 等）に委ねる。上書きが必要になった場合は理由を ADR に残すこと。
//
// 例外 (ADR-0013 追記): CVE-2026-110879 (Jackson の多態デシリアライゼーション検証バイパス,
// 2026-10-28 公開) への対応。Boot 4.1.2 の管理版 databind 3.1.7 と、springdoc 経由の
// Jackson 2 系 databind 2.21.7 が対象のため、修正版を含む BOM へ引き上げる。
dependencyManagement {
    imports {
        mavenBom("tools.jackson:jackson-bom:3.1.8")
        mavenBom("com.fasterxml.jackson:jackson-bom:2.21.8")
    }
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Boot 4 から Flyway の自動設定は starter-flyway が必要（モジュール分割の影響）
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("com.opencsv:opencsv:5.12.0")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    // Mapper の対MySQLテスト用 (CI の Docker 上で実行する)
    testImplementation("org.testcontainers:mysql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}
tasks.withType<Test> {
    useJUnitPlatform()
    // 失敗時に例外メッセージまでコンソールに出す（CIログで原因を追えるように）
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("failed")
        showStandardStreams = true
    }
}
