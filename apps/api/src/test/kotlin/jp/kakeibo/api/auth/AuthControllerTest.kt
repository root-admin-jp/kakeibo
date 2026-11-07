package jp.kakeibo.api.auth

import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.HouseholdRepository
import jp.kakeibo.api.db.UserRepository
import jp.kakeibo.api.db.UserRow
import jp.kakeibo.api.security.JwtService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthControllerTest {
    private val users: UserRepository = mock()
    private val households: HouseholdRepository = mock()
    private val categories: CategoryRepository = mock()
    private val encoder = BCryptPasswordEncoder()
    private val jwt = JwtService("test-secret-test-secret-test-secret-0123456789", 60)
    private val controller = AuthController(users, households, categories, encoder, jwt)

    @Test
    fun `register creates user, household and default categories`() {
        whenever(users.findByEmail("new@example.com")).thenReturn(null)
        whenever(users.insert(any(), any(), any())).thenReturn(42L)
        whenever(households.insert(any(), any())).thenReturn(7L)
        val seq = AtomicLong(100)
        whenever(categories.insert(any(), anyOrNull(), any(), any())).thenAnswer { seq.incrementAndGet() }

        val res = controller.register(RegisterRequest("new@example.com", "password123", "テスト"))

        assertEquals(42L, res.userId)
        assertEquals(7L, res.householdId)
        assertEquals("new@example.com", res.email)
        // 発行されたトークンが同じユーザー/テナントを指すこと
        val parsed = jwt.parse(res.token)
        assertEquals(42L, parsed.userId)
        assertEquals(7L, parsed.householdId)
        // 初期カテゴリ: 大項目の数 + 中項目の数 だけ insert されること
        val expected = AuthController.DEFAULT_CATEGORIES.sumOf { 1 + it.second.size }
        verify(categories, times(expected)).insert(any(), anyOrNull(), any(), any())
        // パスワードはハッシュ化されて保存されること（平文で insert されない）
        verify(users).insert(any(), org.mockito.kotlin.argThat { startsWith("\$2") }, any())
    }

    @Test
    fun `register with duplicate email is conflict`() {
        whenever(users.findByEmail("dup@example.com"))
            .thenReturn(UserRow(1, "dup@example.com", "hash", "既存"))
        val e = assertFailsWith<ResponseStatusException> {
            controller.register(RegisterRequest("dup@example.com", "password123", "x"))
        }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
    }

    @Test
    fun `login succeeds with correct password`() {
        val hash = requireNotNull(encoder.encode("password123"))
        whenever(users.findByEmail("user@example.com")).thenReturn(UserRow(42, "user@example.com", hash, "名前"))
        whenever(households.findIdByOwner(42)).thenReturn(7L)

        val res = controller.login(LoginRequest("user@example.com", "password123"))
        assertEquals(42L, res.userId)
        assertEquals(7L, res.householdId)
        assertEquals("名前", res.displayName)
    }

    @Test
    fun `login with wrong password is unauthorized`() {
        val hash = requireNotNull(encoder.encode("password123"))
        whenever(users.findByEmail("user@example.com")).thenReturn(UserRow(42, "user@example.com", hash, "名前"))
        val e = assertFailsWith<ResponseStatusException> {
            controller.login(LoginRequest("user@example.com", "wrong-password"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
    }

    @Test
    fun `login with unknown email is unauthorized`() {
        whenever(users.findByEmail("nobody@example.com")).thenReturn(null)
        val e = assertFailsWith<ResponseStatusException> {
            controller.login(LoginRequest("nobody@example.com", "password123"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, e.statusCode)
    }
}
