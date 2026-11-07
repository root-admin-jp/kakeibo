package jp.kakeibo.api.account

import jp.kakeibo.api.clearAuth
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.AccountRow
import jp.kakeibo.api.loginAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountControllerTest {
    private val accounts: AccountRepository = mock()
    private val controller = AccountController(accounts)

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
