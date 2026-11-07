package jp.kakeibo.api.summary

import jp.kakeibo.api.clearAuth
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.loginAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals

class SummaryControllerTest {
    private val transactions: TransactionRepository = mock()
    private val controller = SummaryController(transactions)

    @BeforeEach
    fun setUp() = loginAs(userId = 42, householdId = 7)

    @AfterEach
    fun tearDown() = clearAuth()

    @Test
    fun `monthly summary aggregates income expense and category totals`() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        whenever(transactions.monthlyTotals(7, from, to)).thenReturn(100_000L to 40_000L)
        whenever(transactions.totalsByTopCategory(7, from, to, "expense"))
            .thenReturn(listOf("食費" to 30_000L, "交通費" to 10_000L))
        whenever(transactions.totalsByTopCategory(7, from, to, "income"))
            .thenReturn(listOf("給与" to 100_000L))

        val res = controller.monthly("2026-11")

        assertEquals("2026-11", res.month)
        assertEquals(100_000L, res.totalIncome)
        assertEquals(40_000L, res.totalExpense)
        assertEquals(60_000L, res.balance)
        assertEquals(listOf(CategoryTotal("食費", 30_000L), CategoryTotal("交通費", 10_000L)), res.expenseByCategory)
        assertEquals(listOf(CategoryTotal("給与", 100_000L)), res.incomeByCategory)
        verify(transactions).monthlyTotals(eq(7L), eq(from), eq(to))
    }

    @Test
    fun `monthly summary of empty month is zero`() {
        val from = LocalDate.of(2026, 12, 1)
        val to = LocalDate.of(2026, 12, 31)
        whenever(transactions.monthlyTotals(7, from, to)).thenReturn(0L to 0L)
        whenever(transactions.totalsByTopCategory(7, from, to, "expense")).thenReturn(emptyList())
        whenever(transactions.totalsByTopCategory(7, from, to, "income")).thenReturn(emptyList())

        val res = controller.monthly("2026-12")
        assertEquals(0L, res.balance)
        assertEquals(0, res.expenseByCategory.size)
    }
}
