package jp.kakeibo.api.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.HouseholdRepository
import jp.kakeibo.api.db.UserRepository
import jp.kakeibo.api.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank @field:Size(max = 100) val displayName: String,
)
data class LoginRequest(@field:NotBlank val email: String, @field:NotBlank val password: String)
data class AuthResponse(val token: String, val userId: Long, val householdId: Long, val email: String, val displayName: String)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val categories: CategoryRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwt: JwtService,
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
    }

    @PostMapping("/register")
    @Transactional
    fun register(@Valid @RequestBody req: RegisterRequest): AuthResponse {
        if (users.findByEmail(req.email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "email already registered")
        }
        val userId = users.insert(req.email, passwordEncoder.encode(req.password), req.displayName)
        val householdId = households.insert(userId, req.displayName + "の家計")
        var order = 0
        for ((top, children) in DEFAULT_CATEGORIES) {
            val parentId = categories.insert(householdId, null, top, order++)
            for (child in children) categories.insert(householdId, parentId, child, order++)
        }
        return AuthResponse(jwt.issue(userId, householdId, req.email), userId, householdId, req.email, req.displayName)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): AuthResponse {
        val user = users.findByEmail(req.email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password")
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password")
        }
        val householdId = households.findIdByOwner(user.id)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "household not found")
        return AuthResponse(jwt.issue(user.id, householdId, user.email), user.id, householdId, user.email, user.displayName)
    }
}
