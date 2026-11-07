package jp.kakeibo.api.transaction

import jp.kakeibo.api.clearAuth
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.AccountRow
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.CategoryRow
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.db.TransactionRow
import jp.kakeibo.api.loginAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionControllerTest {
    private val transactions: TransactionRepository = mock()
    private val accounts: AccountRepository = mock()
    private val categories: CategoryRepository = mock()
    private val controller = TransactionController(transactions, accounts, categories)

    private val account1 = AccountRow(1, 7, null, "財布", "cash", 0)
    private val account2 = AccountRow(2, 7, null, "銀行", "bank", 0)

    @BeforeEach
    fun setUp() {
        loginAs(userId = 42, householdId = 7)
        whenever(accounts.findById(7, 1)).thenReturn(account1)
        whenever(accounts.findById(7, 2)).thenReturn(account2)
        whenever(categories.findById(7, 11)).thenReturn(CategoryRow(11, 7, 10, "食料品", 1))
    }

    @AfterEach
    fun tearDown() = clearAuth()

    private fun req(
        accountId: Long = 1, toAccountId: Long? = null, categoryId: Long? = 11,
        type: String = "expense", amount: Long = 1200,
    ) = TransactionRequest(accountId, toAccountId, categoryId, type, amount, LocalDate.of(2026, 11, 1), "メモ")

    @Test
    fun `create valid expense`() {
        whenever(transactions.insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull()))
            .thenReturn(55L)
        whenever(transactions.findById(7, 55))
            .thenReturn(TransactionRow(55, 7, 1, null, 11, "expense", 1200, LocalDate.of(2026, 11, 1), "メモ"))

        val res = controller.create(req())
        assertEquals(55L, res.id)
        assertEquals(1200L, res.amount)
        verify(transactions).insert(eq(7L), eq(1L), eq(null), eq(11L), eq("expense"), eq(1200L), any(), anyOrNull())
    }

    @Test
    fun `create with invalid type is bad request`() {
        val e = assertFailsWith<ResponseStatusException> { controller.create(req(type = "gift")) }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `create with account of another household is bad request`() {
        // 他テナントの口座は自テナントの検索では見つからない前提
        val e = assertFailsWith<ResponseStatusException> { controller.create(req(accountId = 99)) }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `transfer to same account is bad request`() {
        val e = assertFailsWith<ResponseStatusException> {
            controller.create(req(type = "transfer", toAccountId = 1, categoryId = null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `transfer without destination is bad request`() {
        val e = assertFailsWith<ResponseStatusException> {
            controller.create(req(type = "transfer", toAccountId = null, categoryId = null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `transfer to missing account is bad request`() {
        val e = assertFailsWith<ResponseStatusException> {
            controller.create(req(type = "transfer", toAccountId = 99, categoryId = null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `create with unknown category is bad request`() {
        val e = assertFailsWith<ResponseStatusException> { controller.create(req(categoryId = 99)) }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `list is scoped to own household and month range`() {
        whenever(transactions.list(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(listOf(TransactionRow(1, 7, 1, null, 11, "expense", 500, LocalDate.of(2026, 11, 3), null)))

        val res = controller.list("2026-11", null, null)
        assertEquals(1, res.size)
        // 月初〜月末の範囲で、自分の householdId のみを引くこと（テナント分離）
        verify(transactions).list(eq(7L), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)), eq(null), eq(null))
    }

    @Test
    fun `update of missing transaction is not found`() {
        whenever(transactions.update(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull()))
            .thenReturn(0)
        val e = assertFailsWith<ResponseStatusException> { controller.update(999, req()) }
        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }

    @Test
    fun `delete of missing transaction is not found`() {
        whenever(transactions.softDelete(any(), any())).thenReturn(0)
        val e = assertFailsWith<ResponseStatusException> { controller.delete(999) }
        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }
}
