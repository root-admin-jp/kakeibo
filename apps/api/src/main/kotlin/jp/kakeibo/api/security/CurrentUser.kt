package jp.kakeibo.api.security

data class CurrentUser(
    val userId: Long,
    val householdId: Long,
    val email: String,
)
