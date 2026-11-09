package jp.kakeibo.api.account

import jp.kakeibo.api.clearAuth
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.AccountRow
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.loginAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountControllerTest {
    private val accounts: AccountRepository = mock()
    private val transactions: TransactionRepository = mock()
    private val controller = AccountController(accounts, transactions)

    @BeforeEach
    fun setUp() = loginAs(userId = 42, householdId = 7)

    @AfterEach
    fun tearDown() = clearAuth()

    @Test
    fun `create with invalid kind is bad request`() {
        val e = assertFailsWith<ResponseStatusException> {
            controller.create(AccountRequest(null, "謎口座", "bitcoin"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `create valid account`() {
        whenever(accounts.insert(any(), anyOrNull(), any(), any())).thenReturn(3L)
        whenever(accounts.findById(7, 3)).thenReturn(AccountRow(3, 7, null, "財布", "cash", 0))
        val res = controller.create(AccountRequest(null, "財布", "cash"))
        assertEquals(3L, res.id)
        assertEquals("cash", res.kind)
        // 残高未指定なら調整は一切しない
        verify(transactions, never()).insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull())
        verify(accounts, never()).addBalanceOffset(any(), any(), any())
    }

    @Test
    fun `create with balance records an adjustment transaction`() {
        whenever(accounts.insert(any(), anyOrNull(), any(), any())).thenReturn(3L)
        whenever(transactions.insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull())).thenReturn(9L)
        whenever(accounts.findById(7, 3)).thenReturn(AccountRow(3, 7, null, "銀行", "bank", 10_000))
        val res = controller.create(AccountRequest(null, "銀行", "bank", currentBalance = 10_000, recordAdjustment = true))
        assertEquals(10_000L, res.balance)
        // 作成直後の算出残高は 0 なので、設定値そのものが残高調整として記録される
        verify(transactions).insert(eq(7L), eq(3L), anyOrNull(), anyOrNull(), eq("adjustment"), eq(10_000L), any(), eq("残高調整"))
        verify(accounts, never()).addBalanceOffset(any(), any(), any())
    }

    @Test
    fun `create with balance without recording only moves the offset`() {
        whenever(accounts.insert(any(), anyOrNull(), any(), any())).thenReturn(3L)
        whenever(accounts.findById(7, 3)).thenReturn(AccountRow(3, 7, null, "銀行", "bank", 5_000, 5_000))
        val res = controller.create(AccountRequest(null, "銀行", "bank", currentBalance = 5_000, recordAdjustment = false))
        assertEquals(5_000L, res.balance)
        verify(accounts).addBalanceOffset(7, 3, 5_000)
        verify(transactions, never()).insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `list returns only own household accounts`() {
        whenever(accounts.listByHousehold(7)).thenReturn(listOf(AccountRow(1, 7, null, "財布", "cash", 500)))
        val res = controller.list()
        assertEquals(1, res.size)
        assertEquals(500L, res[0].balance)
    }

    @Test
    fun `update of missing account is not found`() {
        whenever(accounts.update(any(), any(), anyOrNull(), any(), any())).thenReturn(0)
        val e = assertFailsWith<ResponseStatusException> {
            controller.update(999, AccountRequest(null, "x", "cash"))
        }
        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }

    @Test
    fun `update with balance records only the delta as adjustment`() {
        whenever(accounts.update(any(), any(), anyOrNull(), any(), any())).thenReturn(1)
        whenever(transactions.insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull())).thenReturn(9L)
        whenever(accounts.findById(7, 5)).thenReturn(AccountRow(5, 7, null, "銀行", "bank", 1_000))
        controller.update(5, AccountRequest(null, "銀行", "bank", currentBalance = 1_500, recordAdjustment = true))
        verify(transactions).insert(eq(7L), eq(5L), anyOrNull(), anyOrNull(), eq("adjustment"), eq(500L), any(), eq("残高調整"))
        verify(accounts, never()).addBalanceOffset(any(), any(), any())
    }

    @Test
    fun `update with balance without recording moves offset by negative delta`() {
        whenever(accounts.update(any(), any(), anyOrNull(), any(), any())).thenReturn(1)
        whenever(accounts.findById(7, 5)).thenReturn(AccountRow(5, 7, null, "カード", "credit_card", 1_000))
        // マイナス残高への設定も許可する（差額 -1,500 が offset に流れる）
        controller.update(5, AccountRequest(null, "カード", "credit_card", currentBalance = -500, recordAdjustment = false))
        verify(accounts).addBalanceOffset(7, 5, -1_500)
        verify(transactions, never()).insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `update with unchanged balance does nothing`() {
        whenever(accounts.update(any(), any(), anyOrNull(), any(), any())).thenReturn(1)
        whenever(accounts.findById(7, 5)).thenReturn(AccountRow(5, 7, null, "銀行", "bank", 1_000))
        controller.update(5, AccountRequest(null, "銀行", "bank", currentBalance = 1_000, recordAdjustment = true))
        verify(transactions, never()).insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull())
        verify(accounts, never()).addBalanceOffset(any(), any(), any())
    }

    @Test
    fun `delete account with transactions is conflict`() {
        whenever(accounts.countTransactions(1)).thenReturn(2)
        val e = assertFailsWith<ResponseStatusException> { controller.delete(1) }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
    }

    @Test
    fun `delete of missing account is not found`() {
        whenever(accounts.countTransactions(999)).thenReturn(0)
        whenever(accounts.delete(any(), any())).thenReturn(0)
        val e = assertFailsWith<ResponseStatusException> { controller.delete(999) }
        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }
}
