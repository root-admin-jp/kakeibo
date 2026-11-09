package jp.kakeibo.api.db

import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

// Row型は MyBatis のマッピング対象（無引数生成＋プロパティ設定で埋めるため var + デフォルト値）。
data class UserRow(var id: Long = 0, var email: String = "", var displayName: String = "")
data class LoginCodeRow(
    var id: Long = 0, var email: String = "", var codeHash: String = "",
    var displayName: String? = null, var attempts: Int = 0,
    var expiresAt: LocalDateTime = LocalDateTime.now(), var consumedAt: LocalDateTime? = null,
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
data class PasskeyCredentialRow(
    var id: Long = 0, var userId: Long = 0, var credentialId: String = "",
    var publicKeyCose: String = "", var signCount: Long = 0, var name: String = "",
    var createdAt: LocalDateTime = LocalDateTime.now(), var lastUsedAt: LocalDateTime? = null,
)
data class WebAuthnChallengeRow(
    var id: Long = 0, var challenge: String = "", var userId: Long? = null,
    var kind: String = "", var requestJson: String = "",
    var expiresAt: LocalDateTime = LocalDateTime.now(), var createdAt: LocalDateTime = LocalDateTime.now(),
)
data class HouseholdRow(var id: Long = 0, var ownerUserId: Long = 0, var name: String = "")
data class AccountRow(var id: Long = 0, var householdId: Long = 0, var institutionId: Long? = null, var name: String = "", var kind: String = "", var balance: Long = 0)
data class CategoryRow(var id: Long = 0, var householdId: Long = 0, var parentId: Long? = null, var name: String = "", var sortOrder: Int = 0)
data class TransactionRow(
    var id: Long = 0, var householdId: Long = 0, var accountId: Long = 0, var toAccountId: Long? = null,
    var categoryId: Long? = null, var type: String = "", var amount: Long = 0,
    var transactedOn: LocalDate = LocalDate.now(), var memo: String? = null,
)
data class InstitutionRow(var id: Long = 0, var code: String = "", var name: String = "", var kind: String = "")
data class CategoryTotalRow(var topName: String = "", var total: Long = 0)
data class ImportJobRow(
    var id: Long = 0, var householdId: Long = 0, var accountId: Long? = null,
    var status: String = "", var importedCount: Int = 0, var errorMessage: String? = null,
)

@Repository
class UserRepository(private val mapper: UserMapper) {
    fun findByEmail(email: String): UserRow? = mapper.findByEmail(email)

    fun findById(id: Long): UserRow? = mapper.findById(id)

    fun insert(email: String, displayName: String): Long {
        val row = UserRow(email = email, displayName = displayName)
        mapper.insert(row)
        return row.id
    }
}

@Repository
class PasskeyRepository(private val mapper: PasskeyMapper) {
    fun listByUser(userId: Long): List<PasskeyCredentialRow> = mapper.listByUser(userId)

    fun findByCredentialId(credentialId: String): PasskeyCredentialRow? = mapper.findByCredentialId(credentialId)

    fun insert(userId: Long, credentialId: String, publicKeyCose: String, signCount: Long, name: String): Long {
        val row = PasskeyCredentialRow(
            userId = userId, credentialId = credentialId,
            publicKeyCose = publicKeyCose, signCount = signCount, name = name,
        )
        mapper.insert(row)
        return row.id
    }

    fun updateUsage(credentialId: String, signCount: Long, lastUsedAt: LocalDateTime): Int =
        mapper.updateUsage(credentialId, signCount, lastUsedAt)

    fun deleteByIdAndUser(id: Long, userId: Long): Int = mapper.deleteByIdAndUser(id, userId)
}

@Repository
class WebAuthnChallengeRepository(private val mapper: WebAuthnChallengeMapper) {
    fun insert(challenge: String, userId: Long?, kind: String, requestJson: String, expiresAt: LocalDateTime): Long {
        mapper.deleteExpired(LocalDateTime.now())
        val row = WebAuthnChallengeRow(
            challenge = challenge, userId = userId, kind = kind,
            requestJson = requestJson, expiresAt = expiresAt,
        )
        mapper.insert(row)
        return row.id
    }

    fun findByChallenge(challenge: String): WebAuthnChallengeRow? = mapper.findByChallenge(challenge)

    fun deleteByChallenge(challenge: String): Int = mapper.deleteByChallenge(challenge)
}

@Repository
class LoginCodeRepository(private val mapper: LoginCodeMapper) {
    fun insert(email: String, codeHash: String, displayName: String?, expiresAt: LocalDateTime): Long {
        val row = LoginCodeRow(email = email, codeHash = codeHash, displayName = displayName, expiresAt = expiresAt)
        mapper.insert(row)
        return row.id
    }

    fun findLatestActive(email: String): LoginCodeRow? = mapper.findLatestActive(email)

    fun consume(id: Long): Int = mapper.consume(id)

    fun incrementAttempts(id: Long): Int = mapper.incrementAttempts(id)

    fun consumeAllForEmail(email: String): Int = mapper.consumeAllForEmail(email)
}

@Repository
class HouseholdRepository(private val mapper: HouseholdMapper) {
    fun insert(ownerUserId: Long, name: String): Long {
        val row = HouseholdRow(ownerUserId = ownerUserId, name = name)
        mapper.insert(row)
        return row.id
    }

    fun findIdByOwner(ownerUserId: Long): Long? = mapper.findIdByOwner(ownerUserId)
}

@Repository
class AccountRepository(private val mapper: AccountMapper) {
    fun listByHousehold(householdId: Long): List<AccountRow> = mapper.listByHousehold(householdId)

    fun findById(householdId: Long, id: Long): AccountRow? =
        listByHousehold(householdId).firstOrNull { it.id == id }

    fun insert(householdId: Long, institutionId: Long?, name: String, kind: String): Long {
        val row = AccountRow(householdId = householdId, institutionId = institutionId, name = name, kind = kind)
        mapper.insert(row)
        return row.id
    }

    fun update(householdId: Long, id: Long, institutionId: Long?, name: String, kind: String): Int =
        mapper.update(householdId, id, institutionId, name, kind)

    fun countTransactions(id: Long): Int = mapper.countTransactions(id)

    fun delete(householdId: Long, id: Long): Int = mapper.delete(householdId, id)
}

@Repository
class CategoryRepository(private val mapper: CategoryMapper) {
    fun listByHousehold(householdId: Long): List<CategoryRow> = mapper.listByHousehold(householdId)

    fun findById(householdId: Long, id: Long): CategoryRow? =
        listByHousehold(householdId).firstOrNull { it.id == id }

    fun insert(householdId: Long, parentId: Long?, name: String, sortOrder: Int): Long {
        val row = CategoryRow(householdId = householdId, parentId = parentId, name = name, sortOrder = sortOrder)
        mapper.insert(row)
        return row.id
    }

    fun delete(householdId: Long, id: Long): Int = mapper.delete(householdId, id)
}

@Repository
class TransactionRepository(private val mapper: TransactionMapper) {
    fun list(householdId: Long, from: LocalDate, to: LocalDate, accountId: Long?, categoryId: Long?): List<TransactionRow> =
        mapper.list(householdId, from, to, accountId, categoryId)

    fun findById(householdId: Long, id: Long): TransactionRow? = mapper.findById(householdId, id)

    fun insert(householdId: Long, accountId: Long, toAccountId: Long?, categoryId: Long?, type: String, amount: Long, on: LocalDate, memo: String?): Long {
        val row = TransactionRow(
            householdId = householdId, accountId = accountId, toAccountId = toAccountId,
            categoryId = categoryId, type = type, amount = amount, transactedOn = on, memo = memo,
        )
        mapper.insert(row)
        return row.id
    }

    fun update(householdId: Long, id: Long, accountId: Long, toAccountId: Long?, categoryId: Long?, type: String, amount: Long, on: LocalDate, memo: String?): Int =
        mapper.update(householdId, id, accountId, toAccountId, categoryId, type, amount, on, memo)

    fun softDelete(householdId: Long, id: Long): Int = mapper.softDelete(householdId, id)

    fun monthlyTotals(householdId: Long, from: LocalDate, to: LocalDate): Pair<Long, Long> =
        mapper.sumAmountByType(householdId, from, to, "income") to mapper.sumAmountByType(householdId, from, to, "expense")

    /** type別・大項目（親カテゴリ）別の合計。子カテゴリは親に集約し、未分類は「未分類」。 */
    fun totalsByTopCategory(householdId: Long, from: LocalDate, to: LocalDate, type: String): List<Pair<String, Long>> =
        mapper.totalsByTopCategory(householdId, from, to, type).map { it.topName to it.total }
}

@Repository
class InstitutionRepository(private val mapper: InstitutionMapper) {
    fun listAll(): List<InstitutionRow> = mapper.listAll()
}

@Repository
class ImportJobRepository(private val mapper: ImportJobMapper) {
    fun insert(householdId: Long, accountId: Long?, status: String, imported: Int, error: String?): Long {
        val row = ImportJobRow(
            householdId = householdId, accountId = accountId, status = status,
            importedCount = imported, errorMessage = error,
        )
        mapper.insert(row)
        return row.id
    }
}
