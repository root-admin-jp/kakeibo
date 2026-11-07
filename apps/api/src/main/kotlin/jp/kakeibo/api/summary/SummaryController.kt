package jp.kakeibo.api.summary

import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.security.currentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

data class CategoryTotal(val categoryName: String, val total: Long)
data class MonthlySummary(
    val month: String,
    val totalIncome: Long,
    val totalExpense: Long,
    val balance: Long,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
)

@RestController
@RequestMapping("/api/summary")
class SummaryController(private val transactions: TransactionRepository) {
    @GetMapping("/monthly")
    fun monthly(@RequestParam month: String): MonthlySummary {
        val user = currentUser()
        val ym = YearMonth.parse(month)
        val from = ym.atDay(1); val to = ym.atEndOfMonth()
        val (income, expense) = transactions.monthlyTotals(user.householdId, from, to)
        return MonthlySummary(
            month = month,
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
            expenseByCategory = transactions.totalsByTopCategory(user.householdId, from, to, "expense").map { CategoryTotal(it.first, it.second) },
            incomeByCategory = transactions.totalsByTopCategory(user.householdId, from, to, "income").map { CategoryTotal(it.first, it.second) },
        )
    }
}
