package jp.kakeibo.api.security

import io.jsonwebtoken.JwtException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtServiceTest {
    private val secret = "test-secret-test-secret-test-secret-0123456789"

    @Test
    fun `issue and parse roundtrip`() {
        val service = JwtService(secret, 60)
        val token = service.issue(42, 7, "user@example.com")
        val user = service.parse(token)
        assertEquals(42, user.userId)
        assertEquals(7, user.householdId)
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `token signed with another secret is rejected`() {
        val issuer = JwtService(secret, 60)
        val verifier = JwtService("other-secret-other-secret-other-secret-9876543210", 60)
        val token = issuer.issue(42, 7, "user@example.com")
        assertFailsWith<JwtException> { verifier.parse(token) }
    }

    @Test
    fun `tampered token is rejected`() {
        val service = JwtService(secret, 60)
        val token = service.issue(42, 7, "user@example.com")
        assertFailsWith<JwtException> { service.parse(token + "x") }
    }

    @Test
    fun `expired token is rejected`() {
        val service = JwtService(secret, -1) // 有効期限を過去にする
        val token = service.issue(42, 7, "user@example.com")
        assertFailsWith<JwtException> { service.parse(token) }
    }

    @Test
    fun `garbage string is rejected`() {
        val service = JwtService(secret, 60)
        assertFailsWith<JwtException> { service.parse("not-a-jwt") }
    }
}
