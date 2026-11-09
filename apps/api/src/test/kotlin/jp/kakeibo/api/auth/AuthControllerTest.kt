package jp.kakeibo.api.auth

import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.HouseholdRepository
import jp.kakeibo.api.db.LoginCodeRepository
import jp.kakeibo.api.db.LoginCodeRow
import jp.kakeibo.api.db.UserRepository
import jp.kakeibo.api.db.UserRow
import jp.kakeibo.api.mail.MailService
import jp.kakeibo.api.security.JwtService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthControllerTest {
    private val users: UserRepository = mock()
    private val households: HouseholdRepository = mock()
    private val categories: CategoryRepository = mock()
    private val loginCodes: LoginCodeRepository = mock()
    private val mail: MailService = mock()
    private val jwt = JwtService("test-secret-test-secret-test-secret-0123456789", 60)
    private val controller = AuthController(users, households, categories, loginCodes, mail, jwt, 60L)

    /** コントローラと同じ方式でコードのハッシュを計算する（検証用行の組み立てに使う）。 */
    private fun hashOf(email: String, code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$email:$code".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun activeRow(email: String, code: String, displayName: String? = null): LoginCodeRow =
        LoginCodeRow(
            id = 99, email = email, codeHash = hashOf(email, code), displayName = displayName,
            attempts = 0, expiresAt = LocalDateTime.now().plusMinutes(10), createdAt = LocalDateTime.now(),
        )

    @Test
    fun `request-code sends a 6 digit code by mail and stores only its hash`() {
        whenever(loginCodes.findLatestActive("new@example.com")).thenReturn(null)
        whenever(loginCodes.insert(any(), any(), anyOrNull(), any())).thenReturn(1L)

        val res = controller.requestCode(RequestCodeRequest("new@example.com", "テスト"))

        assertTrue(res.sent)
        // メール本文からコードを取り出し、保存されるのがそのハッシュであることを確認する
        val bodyCaptor = argumentCaptor<String>()
        verify(mail).send(any(), any(), bodyCaptor.capture())
        val code = Regex("""\d{6}""").find(bodyCaptor.firstValue)?.value
        assertTrue(code != null && code.length == 6)
        verify(loginCodes).insert(
            org.mockito.kotlin.eq("new@example.com"),
            org.mockito.kotlin.eq(hashOf("new@example.com", code!!)),
            org.mockito.kotlin.eq("テスト"),
            any(),
        )
    }

    @Test
    fun `request-code within resend interval is too many requests`() {
        whenever(loginCodes.findLatestActive("user@example.com"))
            .thenReturn(activeRow("user@example.com", "123456"))
        val e = assertFailsWith<ResponseStatusException> {
            controller.requestCode(RequestCodeRequest("user@example.com"))
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.statusCode)
    }

    @Test
    fun `verify-code creates account for new user`() {
        whenever(loginCodes.findLatestActive("new@example.com"))
            .thenReturn(activeRow("new@example.com", "123456", "テスト"))
        whenever(users.findByEmail("new@example.com")).thenReturn(null)
        whenever(users.insert(any(), any())).thenReturn(42L)
        whenever(households.insert(any(), any())).thenReturn(7L)
        val seq = AtomicLong(100)
        whenever(categories.insert(any(), anyOrNull(), any(), any())).thenAnswer { seq.incrementAndGet() }

        val res = controller.verifyCode(VerifyCodeRequest("new@example.com", "123456"))

        assertEquals(42L, res.userId)
        assertEquals(7L, res.householdId)
        assertEquals("テスト", res.displayName)
        val parsed = jwt.parse(res.token)
        assertEquals(42L, parsed.userId)
        assertEquals(7L, parsed.householdId)
        val expected = AuthController.DEFAULT_CATEGORIES.sumOf { 1 + it.second.size }
        verify(categories, times(expected)).insert(any(), anyOrNull(), any(), any())
        verify(loginCodes).consume(99)
    }

    @Test
    fun `verify-code logs in existing user`() {
        whenever(loginCodes.findLatestActive("user@example.com"))
            .thenReturn(activeRow("user@example.com", "123456"))
        whenever(users.findByEmail("user@example.com")).thenReturn(UserRow(42, "user@example.com", "名前"))
        whenever(households.findIdByOwner(42)).thenReturn(7L)

        val res = controller.verifyCode(VerifyCodeRequest("user@example.com", "123456"))

        assertEquals(42L, res.userId)
        assertEquals(7L, res.householdId)
        assertEquals("名前", res.displayName)
    }

    @Test
    fun `verify-code with wrong code is unauthorized and counts the attempt`() {
        whenever(loginCodes.findLatestActive("user@example.com"))
            .thenReturn(activeRow("user@example.com", "123456"))
        val e = assertFailsWith<ResponseStatusException> {
            controller.verifyCode(VerifyCodeRequest("user@example.com", "000000"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
        verify(loginCodes).incrementAttempts(99)
    }

    @Test
    fun `verify-code with expired code is unauthorized`() {
        val expired = activeRow("user@example.com", "123456").apply {
            expiresAt = LocalDateTime.now().minusMinutes(1)
        }
        whenever(loginCodes.findLatestActive("user@example.com")).thenReturn(expired)
        val e = assertFailsWith<ResponseStatusException> {
            controller.verifyCode(VerifyCodeRequest("user@example.com", "123456"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
    }

    @Test
    fun `verify-code for new user without display name is bad request`() {
        whenever(loginCodes.findLatestActive("new@example.com"))
            .thenReturn(activeRow("new@example.com", "123456", null))
        whenever(users.findByEmail("new@example.com")).thenReturn(null)
        val e = assertFailsWith<ResponseStatusException> {
            controller.verifyCode(VerifyCodeRequest("new@example.com", "123456"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }
}
