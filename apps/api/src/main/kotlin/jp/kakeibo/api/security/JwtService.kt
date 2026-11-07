package jp.kakeibo.api.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${kakeibo.jwt-secret:}") secret: String,
    @Value("\${kakeibo.jwt-expiration-minutes:1440}") private val expirationMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val key: SecretKey = run {
        val raw = if (secret.isNotBlank()) secret else {
            log.warn("JWT_SECRET is not set. Generating an ephemeral key; tokens will be invalidated on restart.")
            UUID.randomUUID().toString() + UUID.randomUUID().toString()
        }
        Keys.hmacShaKeyFor(raw.toByteArray(Charsets.UTF_8))
    }

    fun issue(userId: Long, householdId: Long, email: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("householdId", householdId)
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): CurrentUser {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return CurrentUser(
            userId = claims.subject.toLong(),
            householdId = (claims["householdId"] as Number).toLong(),
            email = claims["email"] as String,
        )
    }
}
