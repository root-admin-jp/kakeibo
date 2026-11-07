plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "jp.kakeibo"
version = "0.0.1-SNAPSHOT"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }

// Snyk 対応の暫定パッチ上書き (ADR-0011 追記):
// Boot 3.5.16 の BOM より新しいパッチが存在するものだけを同ライン内で引き上げる。
// Spring Framework 6.2 / Security 6.5 はライン内最新が現行版のため対象外 (修正は Boot 4 のみ)。
// Boot を更新したら、このブロックがまだ必要か必ず見直すこと。
dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:2.21.8")
    }
    dependencies {
        dependency("org.apache.tomcat.embed:tomcat-embed-core:10.1.61")
        dependency("org.apache.tomcat.embed:tomcat-embed-websocket:10.1.61")
        dependency("org.apache.tomcat.embed:tomcat-embed-el:10.1.61")
        dependency("ch.qos.logback:logback-classic:1.5.38")
        dependency("ch.qos.logback:logback-core:1.5.38")
        dependency("org.apache.commons:commons-lang3:3.21.0")
        dependency("org.apache.commons:commons-compress:1.28.0")
        dependency("io.swagger.core.v3:swagger-core-jakarta:2.2.55")
    }
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("com.opencsv:opencsv:5.12.0")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    // Mapper の対MySQLテスト用 (CI の Docker 上で実行する)
    testImplementation("org.testcontainers:mysql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
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
