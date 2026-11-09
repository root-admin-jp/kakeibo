package jp.kakeibo.api.auth

import com.yubico.webauthn.RelyingParty
import jp.kakeibo.api.clearAuth
import jp.kakeibo.api.db.HouseholdRepository
import jp.kakeibo.api.db.PasskeyCredentialRow
import jp.kakeibo.api.db.PasskeyRepository
import jp.kakeibo.api.db.UserRepository
import jp.kakeibo.api.db.WebAuthnChallengeRepository
import jp.kakeibo.api.db.WebAuthnChallengeRow
import jp.kakeibo.api.loginAs
import jp.kakeibo.api.security.JwtService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

/**
 * パスキーのチャレンジ検証・一覧・削除の単体テスト。
 * WebAuthn の暗号検証そのものは Yubico ライブラリと E2E（仮想認証器）に任せ、
 * ここでは自前ロジック（チャレンジの消費・期限・所有者チェック）を対象にする。
 */
class PasskeyControllerTest {

    private val users = mock<UserRepository>()
    private val households = mock<HouseholdRepository>()
    private val passkeys = mock<PasskeyRepository>()
    private val challenges = mock<WebAuthnChallengeRepository>()
    private val relyingParty = mock<RelyingParty>()
    private val jwt = JwtService("test-secret-test-secret-test-secret-0123456789", 60)
    private val controller = PasskeyController(users, households, passkeys, challenges, relyingParty, jwt)
    private val json = ObjectMapper()

    @AfterEach
    fun tearDown() = clearAuth()

    private fun challengeRow(kind: String, userId: Long? = 1L, expired: Boolean = false) = WebAuthnChallengeRow(
        id = 1, challenge = "ch-1", userId = userId, kind = kind, requestJson = "{}",
        expiresAt = if (expired) LocalDateTime.now().minusMinutes(1) else LocalDateTime.now().plusMinutes(5),
    )

    @Test
    fun `login verify rejects unknown challenge`() {
        whenever(challenges.findByChallenge("nope")).thenReturn(null)
        val ex = assertThrows<ResponseStatusException> {
            controller.loginVerify(LoginVerifyRequest("nope", json.createObjectNode()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `login verify rejects expired challenge`() {
        whenever(challenges.findByChallenge("ch-1")).thenReturn(challengeRow("login", userId = null, expired = true))
        val ex = assertThrows<ResponseStatusException> {
            controller.loginVerify(LoginVerifyRequest("ch-1", json.createObjectNode()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `login verify rejects register challenge (kind mismatch)`() {
        whenever(challenges.findByChallenge("ch-1")).thenReturn(challengeRow("register"))
        val ex = assertThrows<ResponseStatusException> {
            controller.loginVerify(LoginVerifyRequest("ch-1", json.createObjectNode()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `register verify rejects another users challenge`() {
        loginAs(2L)
        whenever(challenges.findByChallenge("ch-1")).thenReturn(challengeRow("register", userId = 1L))
        val ex = assertThrows<ResponseStatusException> {
            controller.registerVerify(RegisterVerifyRequest(null, "ch-1", json.createObjectNode()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `list returns own passkeys`() {
        loginAs(1L)
        whenever(passkeys.listByUser(1L)).thenReturn(
            listOf(
                PasskeyCredentialRow(
                    id = 7, userId = 1, credentialId = "cred", publicKeyCose = "pk",
                    signCount = 3, name = "MacBook",
                    createdAt = LocalDateTime.of(2026, 11, 9, 10, 0), lastUsedAt = null,
                )
            )
        )
        val res = controller.list()
        assertEquals(1, res.size)
        assertEquals("MacBook", res[0].name)
        assertEquals(null, res[0].lastUsedAt)
    }

    @Test
    fun `delete returns 404 when nothing deleted`() {
        loginAs(1L)
        whenever(passkeys.deleteByIdAndUser(99L, 1L)).thenReturn(0)
        val ex = assertThrows<ResponseStatusException> { controller.delete(99L) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `delete returns 204 on success`() {
        loginAs(1L)
        whenever(passkeys.deleteByIdAndUser(7L, 1L)).thenReturn(1)
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(7L).statusCode)
    }
}
