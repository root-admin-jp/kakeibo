package jp.kakeibo.api.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.HouseholdRepository
import jp.kakeibo.api.db.LoginCodeRepository
import jp.kakeibo.api.db.UserRepository
import jp.kakeibo.api.mail.MailService
import jp.kakeibo.api.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

data class RequestCodeRequest(
    @field:Email @field:NotBlank val email: String,
    // 新規登録用（任意）。検証成功時に未登録メールならこの名前でアカウントを作成する。
    @field:Size(max = 100) val displayName: String? = null,
)

data class VerifyCodeRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Pattern(regexp = "\\d{6}") val code: String,
)

data class RequestCodeResponse(val sent: Boolean = true)

data class AuthResponse(val token: String, val userId: Long, val householdId: Long, val email: String, val displayName: String)

/**
 * パスワードレス認証 (ADR-0014)。登録・ログインはメール OTP の単一フローに統一する。
 * コードを受け取れること自体がメールアドレスの本人確認になる。
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val categories: CategoryRepository,
    private val loginCodes: LoginCodeRepository,
    private val mail: MailService,
    private val jwt: JwtService,
    // 再送間隔はテスト環境でだけ短縮できるよう設定値にする（既定60秒、環境変数 OTP_RESEND_INTERVAL_SECONDS）
    @Value("\${kakeibo.otp.resend-interval-seconds}") private val resendIntervalSeconds: Long,
) {
    companion object {
        // 大項目 > 中項目 の初期カテゴリ（マネーフォワード風）
        val DEFAULT_CATEGORIES: List<Pair<String, List<String>>> = listOf(
            "食費" to listOf("食料品", "外食", "カフェ"),
            "日用品" to listOf("日用品", "ドラッグストア"),
            "住居" to listOf("家賃", "住宅ローン"),
            "水道・光熱費" to listOf("電気", "ガス", "水道"),
            "通信費" to listOf("携帯電話", "インターネット"),
            "交通費" to listOf("電車", "バス", "タクシー", "ガソリン"),
            "医療費" to listOf("病院", "薬"),
            "被服費" to listOf("衣類", "美容院"),
            "趣味・娯楽" to listOf("レジャー", "書籍", "サブスクリプション"),
            "交際費" to listOf("飲み会", "プレゼント"),
            "教育費" to listOf("学費", "習い事"),
            "給与" to listOf("給料", "賞与"),
            "その他収入" to listOf("副収入", "お小遣い"),
            "その他" to listOf("その他"),
        )

        /** コードの有効期限（分） */
        const val CODE_TTL_MINUTES = 10L

        /** 1コードあたりの検証試行上限 */
        const val MAX_ATTEMPTS = 5
    }

    private val random = SecureRandom()

    /**
     * OTP コードを発行してメール送信する。登録済みかどうかで応答を変えない
     * （メールアドレスの存在を漏らさないため、常に同じ応答を返す）。
     */
    @PostMapping("/request-code")
    @Transactional
    fun requestCode(@Valid @RequestBody req: RequestCodeRequest): RequestCodeResponse {
        val email = req.email.trim().lowercase()
        val now = LocalDateTime.now()
        val latest = loginCodes.findLatestActive(email)
        if (latest != null && latest.createdAt.isAfter(now.minusSeconds(resendIntervalSeconds))) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "please wait before requesting a new code")
        }
        loginCodes.consumeAllForEmail(email)
        val code = "%06d".format(random.nextInt(1_000_000))
        val displayName = req.displayName?.trim()?.takeIf { it.isNotEmpty() }
        loginCodes.insert(email, hashCode(email, code), displayName, now.plusMinutes(CODE_TTL_MINUTES))
        mail.send(email, "Kakeibo ログインコード", "Kakeibo のログインコード: $code （有効期限は $CODE_TTL_MINUTES 分です）")
        return RequestCodeResponse()
    }

    /** OTP コードを検証し、既存ユーザーならログイン、新規ならアカウントを作成してログインする。 */
    @PostMapping("/verify-code")
    @Transactional
    fun verifyCode(@Valid @RequestBody req: VerifyCodeRequest): AuthResponse {
        val email = req.email.trim().lowercase()
        val row = loginCodes.findLatestActive(email)
        if (row == null || row.expiresAt.isBefore(LocalDateTime.now()) || row.attempts >= MAX_ATTEMPTS) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired code")
        }
        val expected = hashCode(email, req.code.trim())
        if (!MessageDigest.isEqual(row.codeHash.toByteArray(StandardCharsets.UTF_8), expected.toByteArray(StandardCharsets.UTF_8))) {
            loginCodes.incrementAttempts(row.id)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired code")
        }
        loginCodes.consume(row.id)

        val user = users.findByEmail(email)
        if (user != null) {
            val householdId = households.findIdByOwner(user.id)
                ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "household not found")
            return AuthResponse(jwt.issue(user.id, householdId, user.email), user.id, householdId, user.email, user.displayName)
        }

        val displayName = row.displayName?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "display name is required for new registration")
        val userId = users.insert(email, displayName)
        val householdId = households.insert(userId, displayName + "の家計")
        var order = 0
        for ((top, children) in DEFAULT_CATEGORIES) {
            val parentId = categories.insert(householdId, null, top, order++)
            for (child in children) categories.insert(householdId, parentId, child, order++)
        }
        return AuthResponse(jwt.issue(userId, householdId, email), userId, householdId, email, displayName)
    }

    /** コード本体は保存せず、メールアドレスと組み合わせた SHA-256 ハッシュのみを保持する。 */
    private fun hashCode(email: String, code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$email:$code".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
