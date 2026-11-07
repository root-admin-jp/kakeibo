package jp.kakeibo.api.db

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Statement
import java.time.LocalDate

data class UserRow(val id: Long, val email: String, val passwordHash: String, val displayName: String)
data class AccountRow(val id: Long, val householdId: Long, val institutionId: Long?, val name: String, val kind: String, val balance: Long)
data class CategoryRow(val id: Long, val householdId: Long, val parentId: Long?, val name: String, val sortOrder: Int)
data class TransactionRow(
    val id: Long, val householdId: Long, val accountId: Long, val toAccountId: Long?,
    val categoryId: Long?, val type: String, val amount: Long, val transactedOn: LocalDate, val memo: String?,
)
data class InstitutionRow(val id: Long, val code: String, val name: String, val kind: String)

@Repository
class UserRepository(private val jdbc: JdbcTemplate) {
    fun findByEmail(email: String): UserRow? = jdbc.query(
        "SELECT id, email, password_hash, display_name FROM users WHERE email = ?",
        { rs, _ -> UserRow(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"), rs.getString("display_name")) },
        email,
    ).firstOrNull()

    fun insert(email: String, passwordHash: String, displayName: String): Long {
        val kh = GeneratedKeyHolder()
        jdbc.update({ con ->
            val ps = con.prepareStatement(
                "INSERT INTO users (email, password_hash, display_name) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS,
            )
            ps.setString(1, email); ps.setString(2, passwordHash); ps.setString(3, displayName); ps
        }, kh)
        return kh.key!!.toLong()
    }
}

@Repository
class HouseholdRepository(private val jdbc: JdbcTemplate) {
    fun insert(ownerUserId: Long, name: String): Long {
        val kh = GeneratedKeyHolder()
        jdbc.update({ con ->
            val ps = con.prepareStatement(
                "INSERT INTO households (owner_user_id, name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS,
            )
            ps.setLong(1, ownerUserId); ps.setString(2, name); ps
        }, kh)
        return kh.key!!.toLong()
    }

    fun findIdByOwner(ownerUserId: Long): Long? =
        jdbc.queryForObject("SELECT id FROM households WHERE owner_user_id = ?", Long::class.java, ownerUserId)
}

@Repository
class AccountRepository(private val jdbc: JdbcTemplate) {
    fun listByHousehold(householdId: Long): List<AccountRow> = jdbc.query(
        """
        SELECT a.id, a.household_id, a.institution_id, a.name, a.kind,
          COALESCE(SUM(CASE
            WHEN t.type = 'income' AND t.account_id = a.id THEN t.amount
            WHEN t.type = 'expense' AND t.account_id = a.id THEN -t.amount
            WHEN t.type = 'transfer' AND t.account_id = a.id THEN -t.amount
            WHEN t.type = 'transfer' AND t.to_account_id = a.id THEN t.amount
            ELSE 0 END), 0) AS balance
        FROM accounts a
        LEFT JOIN transactions t ON (t.account_id = a.id OR t.to_account_id = a.id) AND t.deleted_at IS NULL
        WHERE a.household_id = ?
        GROUP BY a.id, a.household_id, a.institution_id, a.name, a.kind
        ORDER BY a.id
        """.trimIndent(),
        { rs, _ -> AccountRow(rs.getLong("id"), rs.getLong("household_id"), rs.getLong("institution_id").takeIf { !rs.wasNull() }, rs.getString("name"), rs.getString("kind"), rs.getLong("balance")) },
        householdId,
    )

    fun findById(householdId: Long, id: Long): AccountRow? =
        listByHousehold(householdId).firstOrNull { it.id == id }

    fun insert(householdId: Long, institutionId: Long?, name: String, kind: String): Long {
        val kh = GeneratedKeyHolder()
        jdbc.update({ con ->
            val ps = con.prepareStatement(
                "INSERT INTO accounts (household_id, institution_id, name, kind) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS,
            )
            ps.setLong(1, householdId)
            if (institutionId == null) ps.setNull(2, java.sql.Types.BIGINT) else ps.setLong(2, institutionId)
            ps.setString(3, name); ps.setString(4, kind); ps
        }, kh)
        return kh.key!!.toLong()
    }

    fun update(householdId: Long, id: Long, institutionId: Long?, name: String, kind: String): Int =
        jdbc.update(
            "UPDATE accounts SET institution_id = ?, name = ?, kind = ? WHERE household_id = ? AND id = ?",
            institutionId, name, kind, householdId, id,
        )

    fun countTransactions(id: Long): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE (account_id = ? OR to_account_id = ?) AND deleted_at IS NULL", Int::class.java, id, id) ?: 0

    fun delete(householdId: Long, id: Long): Int =
        jdbc.update("DELETE FROM accounts WHERE household_id = ? AND id = ?", householdId, id)
}

@Repository
class CategoryRepository(private val jdbc: JdbcTemplate) {
    fun listByHousehold(householdId: Long): List<CategoryRow> = jdbc.query(
        "SELECT id, household_id, parent_id, name, sort_order FROM categories WHERE household_id = ? ORDER BY sort_order, id",
        { rs, _ -> CategoryRow(rs.getLong("id"), rs.getLong("household_id"), rs.getLong("parent_id").takeIf { !rs.wasNull() }, rs.getString("name"), rs.getInt("sort_order")) },
        householdId,
    )

    fun findById(householdId: Long, id: Long): CategoryRow? =
        listByHousehold(householdId).firstOrNull { it.id == id }

    fun insert(householdId: Long, parentId: Long?, name: String, sortOrder: Int): Long {
        val kh = GeneratedKeyHolder()
        jdbc.update({ con ->
            val ps = con.prepareStatement(
                "INSERT INTO categories (household_id, parent_id, name, sort_order) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS,
            )
            ps.setLong(1, householdId)
            if (parentId == null) ps.setNull(2, java.sql.Types.BIGINT) else ps.setLong(2, parentId)
            ps.setString(3, name); ps.setInt(4, sortOrder); ps
        }, kh)
        return kh.key!!.toLong()
    }

    fun delete(householdId: Long, id: Long): Int =
        jdbc.update("DELETE FROM categories WHERE household_id = ? AND id = ?", householdId, id)
}

@Repository
class TransactionRepository(private val jdbc: JdbcTemplate) {
    private val mapper = { rs: java.sql.ResultSet, _: Int ->
        TransactionRow(
            rs.getLong("id"), rs.getLong("household_id"), rs.getLong("account_id"),
            rs.getLong("to_account_id").takeIf { !rs.wasNull() },
            rs.getLong("category_id").takeIf { !rs.wasNull() },
            rs.getString("type"), rs.getLong("amount"), rs.getDate("transacted_on").toLocalDate(), rs.getString("memo"),
        )
    }

    fun list(householdId: Long, from: LocalDate, to: LocalDate, accountId: Long?, categoryId: Long?): List<TransactionRow> {
        val sql = StringBuilder(
            "SELECT * FROM transactions WHERE household_id = ? AND deleted_at IS NULL AND transacted_on BETWEEN ? AND ?",
        )
        val args = mutableListOf<Any>(householdId, from, to)
        if (accountId != null) { sql.append(" AND (account_id = ? OR to_account_id = ?)"); args.add(accountId); args.add(accountId) }
        if (categoryId != null) { sql.append(" AND category_id = ?"); args.add(categoryId) }
        sql.append(" ORDER BY transacted_on DESC, id DESC")
        return jdbc.query(sql.toString(), mapper, *args.toTypedArray())
    }

    fun findById(householdId: Long, id: Long): TransactionRow? = jdbc.query(
        "SELECT * FROM transactions WHERE household_id = ? AND id = ? AND deleted_at IS NULL", mapper, householdId, id,
    ).firstOrNull()

    fun insert(householdId: Long, accountId: Long, toAccountId: Long?, categoryId: Long?, type: String, amount: Long, on: LocalDate, memo: String?): Long {
        val kh = GeneratedKeyHolder()
        jdbc.update({ con ->
            val ps = con.prepareStatement(
                "INSERT INTO transactions (household_id, account_id, to_account_id, category_id, type, amount, transacted_on, memo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            )
            ps.setLong(1, householdId); ps.setLong(2, accountId)
            if (toAccountId == null) ps.setNull(3, java.sql.Types.BIGINT) else ps.setLong(3, toAccountId)
            if (categoryId == null) ps.setNull(4, java.sql.Types.BIGINT) else ps.setLong(4, categoryId)
            ps.setString(5, type); ps.setLong(6, amount); ps.setDate(7, java.sql.Date.valueOf(on)); ps.setString(8, memo); ps
        }, kh)
        return kh.key!!.toLong()
    }

    fun update(householdId: Long, id: Long, accountId: Long, toAccountId: Long?, categoryId: Long?, type: String, amount: Long, on: LocalDate, memo: String?): Int =
        jdbc.update(
            "UPDATE transactions SET account_id = ?, to_account_id = ?, category_id = ?, type = ?, amount = ?, transacted_on = ?, memo = ? WHERE household_id = ? AND id = ? AND deleted_at IS NULL",
            accountId, toAccountId, categoryId, type, amount, on, memo, householdId, id,
        )

    fun softDelete(householdId: Long, id: Long): Int =
        jdbc.update("UPDATE transactions SET deleted_at = CURRENT_TIMESTAMP WHERE household_id = ? AND id = ? AND deleted_at IS NULL", householdId, id)

    fun monthlyTotals(householdId: Long, from: LocalDate, to: LocalDate): Pair<Long, Long> {
        val income = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE household_id = ? AND deleted_at IS NULL AND type='income' AND transacted_on BETWEEN ? AND ?",
            Long::class.java, householdId, from, to,
        ) ?: 0
        val expense = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE household_id = ? AND deleted_at IS NULL AND type='expense' AND transacted_on BETWEEN ? AND ?",
            Long::class.java, householdId, from, to,
        ) ?: 0
        return income to expense
    }

    /** type別・大項目（親カテゴリ）別の合計。子カテゴリは親に集約し、未分類は「未分類」。 */
    fun totalsByTopCategory(householdId: Long, from: LocalDate, to: LocalDate, type: String): List<Pair<String, Long>> =
        jdbc.query(
            """
            SELECT COALESCE(parent.name, cat.name, '未分類') AS top_name, SUM(t.amount) AS total
            FROM transactions t
            LEFT JOIN categories cat ON cat.id = t.category_id
            LEFT JOIN categories parent ON parent.id = cat.parent_id
            WHERE t.household_id = ? AND t.deleted_at IS NULL AND t.type = ? AND t.transacted_on BETWEEN ? AND ?
            GROUP BY top_name ORDER BY total DESC
            """.trimIndent(),
            { rs, _ -> rs.getString("top_name") to rs.getLong("total") },
            householdId, type, from, to,
        )
}

@Repository
class InstitutionRepository(private val jdbc: JdbcTemplate) {
    fun listAll(): List<InstitutionRow> = jdbc.query(
        "SELECT id, code, name, kind FROM financial_institutions ORDER BY id",
        { rs, _ -> InstitutionRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("kind")) },
    )
}

@Repository
class ImportJobRepository(private val jdbc: JdbcTemplate) {
    fun insert(householdId: Long, accountId: Long?, status: String, imported: Int, error: String?): Long {
        val kh = GeneratedKeyHolder()
        jdbc.update({ con ->
            val ps = con.prepareStatement(
                "INSERT INTO import_jobs (household_id, account_id, source, status, imported_count, error_message) VALUES (?, ?, 'generic_csv', ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            )
            ps.setLong(1, householdId)
            if (accountId == null) ps.setNull(2, java.sql.Types.BIGINT) else ps.setLong(2, accountId)
            ps.setString(3, status); ps.setInt(4, imported); ps.setString(5, error); ps
        }, kh)
        return kh.key!!.toLong()
    }
}
