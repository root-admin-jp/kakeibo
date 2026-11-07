package jp.kakeibo.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KakeiboApplication

fun main(args: Array<String>) {
    runApplication<KakeiboApplication>(*args)
}
