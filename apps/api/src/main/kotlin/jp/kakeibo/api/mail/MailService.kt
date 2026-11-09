package jp.kakeibo.api.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * メール送信の共通口 (ADR-0014)。実装は kakeibo.mail.mode (= MAIL_MODE) で切り替える。
 * コード側は特定の送信サービスに依存せず、SMTP の接続先は環境変数で設定する。
 */
interface MailService {
    fun send(to: String, subject: String, body: String)
}

/** 開発用: 送信せずサーバーログに出力する。E2E はこのログ行から OTP コードを拾う。 */
@Service
@ConditionalOnProperty(name = ["kakeibo.mail.mode"], havingValue = "log", matchIfMissing = true)
class LogMailService : MailService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(to: String, subject: String, body: String) {
        log.info("[MAIL:LOG] to={} subject={} body={}", to, subject, body)
    }
}

/** 本番用: SMTP で実際に送信する（JavaMailSender は spring.mail.* の環境変数設定を使う）。 */
@Service
@ConditionalOnProperty(name = ["kakeibo.mail.mode"], havingValue = "smtp")
class SmtpMailService(
    private val javaMailSender: JavaMailSender,
    @Value("\${kakeibo.mail.from}") private val from: String,
) : MailService {
    override fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage()
        message.setFrom(from)
        message.setTo(to)
        message.subject = subject
        message.text = body
        javaMailSender.send(message)
    }
}
