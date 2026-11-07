package jp.kakeibo.api

import jp.kakeibo.api.security.CurrentUser
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/** currentUser() は SecurityContextHolder から読むため、単体テストではここで認証状態を再現する。 */
fun loginAs(userId: Long = 42, householdId: Long = 7, email: String = "user@example.com") {
    val user = CurrentUser(userId, householdId, email)
    SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(user, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
}

fun clearAuth() = SecurityContextHolder.clearContext()
