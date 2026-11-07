package jp.kakeibo.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            try {
                val user = jwtService.parse(header.removePrefix("Bearer ").trim())
                val auth = UsernamePasswordAuthenticationToken(user, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
                SecurityContextHolder.getContext().authentication = auth
            } catch (e: Exception) {
                SecurityContextHolder.clearContext()
            }
        }
        chain.doFilter(request, response)
    }
}

fun currentUser(): CurrentUser =
    SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        ?: throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "unauthorized")
