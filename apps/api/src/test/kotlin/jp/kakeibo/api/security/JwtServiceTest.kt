package jp.kakeibo.api.security

import kotlin.test.Test
import kotlin.test.assertEquals

class JwtServiceTest {
    @Test
    fun `issue and parse roundtrip`() {
        val service = JwtService("test-secret-test-secret-test-secret-0123456789", 60)
        val token = service.issue(42, 7, "user@example.com")
        val user = service.parse(token)
        assertEquals(42, user.userId)
        assertEquals(7, user.householdId)
        assertEquals("user@example.com", user.email)
    }
}
