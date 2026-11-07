# ADR-0002 APIは Kotlin + Spring Boot 3

- 状態: Accepted (2026-11-07, C提案どおりで承認)

## 決定
Kotlin + Spring Boot 3.x、Gradle (Kotlin DSL)。DBアクセスは jOOQ。

## 理由
Kotlin指定のもとでエコシステムとOpenAPI生成が充実しているため。

## 影響
JVMで動作する。ローカルは docker-compose で動かす。
