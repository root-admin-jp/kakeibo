package jp.kakeibo.api.db

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * MyBatis Mapper (ADR-0009)。SQL本体は resources の mapper ディレクトリの XML に置く。
 * メソッド名 = XMLのステートメントID、namespace = このインターフェースのFQN。
 * スカラ引数には @Param を付け、Kotlinの引数名に依存しないようにする。
 * INSERTはRowオブジェクトを渡し、生成キーは useGeneratedKeys で Row.id に書き戻す。
 */

@Mapper
interface UserMapper {
    fun findByEmail(@Param("email") email: String): UserRow?
    fun findById(@Param("id") id: Long): UserRow?
    fun insert(row: UserRow): Int
}

@Mapper
interface PasskeyMapper {
    fun listByUser(@Param("userId") userId: Long): List<PasskeyCredentialRow>
    fun findByCredentialId(@Param("credentialId") credentialId: String): PasskeyCredentialRow?
    fun insert(row: PasskeyCredentialRow): Int
    fun updateUsage(
        @Param("credentialId") credentialId: String,
        @Param("signCount") signCount: Long,
        @Param("lastUsedAt") lastUsedAt: LocalDateTime,
    ): Int
    fun deleteByIdAndUser(@Param("id") id: Long, @Param("userId") userId: Long): Int
}

@Mapper
interface WebAuthnChallengeMapper {
    fun insert(row: WebAuthnChallengeRow): Int
    fun findByChallenge(@Param("challenge") challenge: String): WebAuthnChallengeRow?
    fun deleteByChallenge(@Param("challenge") challenge: String): Int
    fun deleteExpired(@Param("now") now: LocalDateTime): Int
}

@Mapper
interface LoginCodeMapper {
    fun insert(row: LoginCodeRow): Int

    /** 未消費の最新コード（有効期限切れ・試行上限も含む。判定は呼び出し側で行う）。 */
    fun findLatestActive(@Param("email") email: String): LoginCodeRow?

    fun consume(@Param("id") id: Long): Int
    fun incrementAttempts(@Param("id") id: Long): Int
    fun consumeAllForEmail(@Param("email") email: String): Int
}

@Mapper
interface HouseholdMapper {
    fun insert(row: HouseholdRow): Int
    fun findIdByOwner(@Param("ownerUserId") ownerUserId: Long): Long?
}

@Mapper
interface AccountMapper {
    fun listByHousehold(@Param("householdId") householdId: Long): List<AccountRow>
    fun insert(row: AccountRow): Int
    fun update(
        @Param("householdId") householdId: Long, @Param("id") id: Long,
        @Param("institutionId") institutionId: Long?, @Param("name") name: String, @Param("kind") kind: String,
    ): Int
    fun countTransactions(@Param("id") id: Long): Int
    fun delete(@Param("householdId") householdId: Long, @Param("id") id: Long): Int
}

@Mapper
interface CategoryMapper {
    fun listByHousehold(@Param("householdId") householdId: Long): List<CategoryRow>
    fun insert(row: CategoryRow): Int
    fun delete(@Param("householdId") householdId: Long, @Param("id") id: Long): Int
}

@Mapper
interface TransactionMapper {
    fun list(
        @Param("householdId") householdId: Long, @Param("from") from: LocalDate, @Param("to") to: LocalDate,
        @Param("accountId") accountId: Long?, @Param("categoryId") categoryId: Long?,
    ): List<TransactionRow>
    fun findById(@Param("householdId") householdId: Long, @Param("id") id: Long): TransactionRow?
    fun insert(row: TransactionRow): Int
    fun update(
        @Param("householdId") householdId: Long, @Param("id") id: Long,
        @Param("accountId") accountId: Long, @Param("toAccountId") toAccountId: Long?,
        @Param("categoryId") categoryId: Long?, @Param("type") type: String,
        @Param("amount") amount: Long, @Param("transactedOn") transactedOn: LocalDate, @Param("memo") memo: String?,
    ): Int
    fun softDelete(@Param("householdId") householdId: Long, @Param("id") id: Long): Int
    fun sumAmountByType(
        @Param("householdId") householdId: Long, @Param("from") from: LocalDate,
        @Param("to") to: LocalDate, @Param("type") type: String,
    ): Long
    fun totalsByTopCategory(
        @Param("householdId") householdId: Long, @Param("from") from: LocalDate,
        @Param("to") to: LocalDate, @Param("type") type: String,
    ): List<CategoryTotalRow>
}

@Mapper
interface InstitutionMapper {
    fun listAll(): List<InstitutionRow>
}

@Mapper
interface ImportJobMapper {
    fun insert(row: ImportJobRow): Int
}
