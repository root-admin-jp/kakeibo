package jp.kakeibo.api.config

import jp.kakeibo.api.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    @Value("\${kakeibo.cors.allowed-origin-patterns}") private val allowedOriginPatternsRaw: String,
) {

    // パスワード認証は撤去済み (ADR-0014)。認証は OTP / パスキーで検証し、セッションは JWT で保持する。
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            // 許可オリジンは環境変数で差し替え可能（決定R、application.yml の kakeibo.cors）。
            // 既定は localhost のみ。Tailscale 名など localhost 以外のオリジンから使う場合はデプロイ側で追加する。
            allowedOriginPatterns = allowedOriginPatternsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
