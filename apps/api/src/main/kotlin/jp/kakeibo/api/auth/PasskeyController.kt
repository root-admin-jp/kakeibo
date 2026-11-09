package jp.kakeibo.api.auth

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jp.kakeibo.api.db.HouseholdRepository
import jp.kakeibo.api.db.PasskeyRepository
import jp.kakeibo.api.db.UserRepository
import jp.kakeibo.api.db.WebAuthnChallengeRepository
import jp.kakeibo.api.db.WebAuthnChallengeRow
import jp.kakeibo.api.security.JwtService
import jp.kakeibo.api.security.currentUser
import jp.kakeibo.api.webauthn.YubicoCredentialRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

data class PasskeyResponse(
    val id: Long, val name: String,
    val createdAt: LocalDateTime?, val lastUsedAt: LocalDateTime?,
)

data class RegisterVerifyRequest(
    @field:Size(max = 100) val name: String? = null,
    @field:NotBlank val challenge: String,
    val response: JsonNode,
)

data class LoginVerifyRequest(
    @field:NotBlank val challenge: String,
    val response: JsonNode,
)

/**
 * パスキー (WebAuthn) の登録・一覧・削除と、パスキーでのログイン (ADR-0014、決定O)。
 *
 * 登録はログイン後のみ（/api/passkeys 配下は要 JWT）。ログインはメールアドレス不要の
 * discoverable credential 前提で、チャレンジは webauthn_challenges に保存し検証時に消費する。
 * 検証は Yubico ライブラリに任せ、成功したら OTP ログインと同じ JWT を発行する。
 */
@RestController
class PasskeyController(
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val passkeys: PasskeyRepository,
    private val challenges: WebAuthnChallengeRepository,
    private val relyingParty: RelyingParty,
    private val jwt: JwtService,
) {

    // ---- 登録（要ログイン） ----

    @PostMapping("/api/passkeys/register/options", produces = ["application/json"])
    fun registerOptions(): ResponseEntity<String> {
        val me = currentUser()
        val user = users.findById(me.userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val options = relyingParty.startRegistration(
            StartRegistrationOptions.builder()
                .user(
                    UserIdentity.builder()
                        .name(user.email)
                        .displayName(user.displayName)
                        .id(YubicoCredentialRepository.handleOf(user.id))
                        .build()
                )
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build()
                )
                .build()
        )
        challenges.insert(
            options.challenge.base64Url, user.id, KIND_REGISTER,
            options.toJson(), LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES),
        )
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(options.toJson())
    }

    @PostMapping("/api/passkeys/register/verify")
    fun registerVerify(@Valid @RequestBody req: RegisterVerifyRequest): PasskeyResponse {
        val me = currentUser()
        val row = takeChallenge(req.challenge, KIND_REGISTER)
        if (row.userId != me.userId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "challenge user mismatch")
        }
        val result = try {
            relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                    .request(PublicKeyCredentialCreationOptions.fromJson(row.requestJson))
                    .response(PublicKeyCredential.parseRegistrationResponseJson(req.response.toString()))
                    .build()
            )
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "passkey registration failed")
        }
        val name = req.name?.takeIf { it.isNotBlank() } ?: "パスキー"
        val id = passkeys.insert(
            me.userId, result.keyId.id.base64Url, result.publicKeyCose.base64Url,
            result.signatureCount, name,
        )
        val saved = passkeys.listByUser(me.userId).first { it.id == id }
        return PasskeyResponse(saved.id, saved.name, saved.createdAt, saved.lastUsedAt)
    }

    @GetMapping("/api/passkeys")
    fun list(): List<PasskeyResponse> =
        passkeys.listByUser(currentUser().userId)
            .map { PasskeyResponse(it.id, it.name, it.createdAt, it.lastUsedAt) }

    @DeleteMapping("/api/passkeys/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        if (passkeys.deleteByIdAndUser(id, currentUser().userId) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return ResponseEntity.noContent().build()
    }

    // ---- パスキーログイン（公開） ----

    @PostMapping("/api/auth/passkey/login/options", produces = ["application/json"])
    fun loginOptions(): ResponseEntity<String> {
        val request = relyingParty.startAssertion(
            StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build()
        )
        val pkOptions = request.publicKeyCredentialRequestOptions
        challenges.insert(
            pkOptions.challenge.base64Url, null, KIND_LOGIN,
            request.toJson(), LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES),
        )
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(pkOptions.toCredentialsGetJson())
    }

    @PostMapping("/api/auth/passkey/login/verify")
    fun loginVerify(@Valid @RequestBody req: LoginVerifyRequest): AuthResponse {
        val row = takeChallenge(req.challenge, KIND_LOGIN)
        val result = try {
            relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(row.requestJson))
                    .response(PublicKeyCredential.parseAssertionResponseJson(req.response.toString()))
                    .build()
            )
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "passkey login failed")
        }
        if (!result.isSuccess) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "passkey login failed")
        }
        val userId = YubicoCredentialRepository.userIdOf(result.userHandle)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "passkey login failed")
        val user = users.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "passkey login failed")
        passkeys.updateUsage(result.credentialId.base64Url, result.signatureCount, LocalDateTime.now())
        val householdId = households.findIdByOwner(user.id)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "passkey login failed")
        return AuthResponse(
            jwt.issue(user.id, householdId, user.email),
            user.id, householdId, user.email, user.displayName,
        )
    }

    /** チャレンジを取り出して消費する（ワンタイム）。種別・期限も検証する。 */
    private fun takeChallenge(challenge: String, kind: String): WebAuthnChallengeRow {
        val row = challenges.findByChallenge(challenge)
        if (row == null || row.kind != kind) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid challenge")
        }
        challenges.deleteByChallenge(challenge)
        if (row.expiresAt.isBefore(LocalDateTime.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "challenge expired")
        }
        return row
    }

    companion object {
        private const val KIND_REGISTER = "register"
        private const val KIND_LOGIN = "login"
        private const val CHALLENGE_TTL_MINUTES = 5L
    }
}
