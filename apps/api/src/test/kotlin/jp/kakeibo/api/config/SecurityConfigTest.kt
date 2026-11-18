package jp.kakeibo.api.config

import jp.kakeibo.api.security.JwtAuthenticationFilter
import jp.kakeibo.api.security.JwtService
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecurityConfigTest {

    private fun configWith(patterns: String): SecurityConfig {
        val jwtService = JwtService("test-secret-test-secret-test-secret-0123456789", 1440)
        return SecurityConfig(JwtAuthenticationFilter(jwtService), patterns)
    }

    @Test
    fun `parses comma separated origin patterns`() {
        val config = configWith("http://localhost:*, http://example.ts.net:*")
        val request = MockHttpServletRequest("GET", "/api/auth/request-code")
        val cors = config.corsConfigurationSource().getCorsConfiguration(request)
        assertEquals(listOf("http://localhost:*", "http://example.ts.net:*"), cors?.allowedOriginPatterns)
    }

    @Test
    fun `checkOrigin accepts configured origin and rejects others`() {
        val config = configWith("http://localhost:*")
        val request = MockHttpServletRequest("GET", "/api/accounts")
        val cors = config.corsConfigurationSource().getCorsConfiguration(request)
        assertEquals("http://localhost:3000", cors?.checkOrigin("http://localhost:3000"))
        assertNull(cors?.checkOrigin("http://example.ts.net:3000"))
    }
}
