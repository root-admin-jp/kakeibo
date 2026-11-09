package jp.kakeibo.api.transaction

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.db.TransactionRow
import jp.kakeibo.api.security.currentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.YearMonth

data class TransactionRequest(
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    @field:NotBlank val type: String,
    @field:Positive val amount: Long,
    val transactedOn: LocalDate,
    val memo: String? = null,
)
data class TransactionResponse(
    val id: Long, val accountId: Long, val toAccountId: Long?, val categoryId: Long?,
    val type: String, val amount: Long, val transactedOn: LocalDate, val memo: String?,
)
fun TransactionRow.toResponse() = TransactionResponse(id, accountId, toAccountId, categoryId, type, amount, transactedOn, memo)

@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) {
    companion object { val TYPES = setOf("income", "expense", "transfer") }

    private fun validate(req: TransactionRequest) {
        val user = currentUser()
        if (req.type !in TYPES) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid type: ${req.type}")
        if (accounts.findById(user.householdId, req.accountId) == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "account not found")
        }
        if (req.type == "transfer") {
            if (req.toAccountId == null || req.toAccountId == req.accountId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "transfer requires a different toAccountId")
            }
            if (accounts.findById(user.householdId, req.toAccountId) == null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "to account not found")
            }
        }
        if (req.categoryId != null && categories.findById(user.householdId, req.categoryId) == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "category not found")
        }
    }

    @GetMapping
    fun list(
        @RequestParam month: String,
        @RequestParam(required = false) accountId: Long?,
        @RequestParam(required = false) categoryId: Long?,
    ): List<TransactionResponse> {
        val ym = YearMonth.parse(month)
        return transactions.list(currentUser().householdId, ym.atDay(1), ym.atEndOfMonth(), accountId, categoryId).map { it.toResponse() }
    }

    @PostMapping
    fun create(@Valid @RequestBody req: TransactionRequest): TransactionResponse {
        validate(req)
        val user = currentUser()
        val id = transactions.insert(user.householdId, req.accountId, req.toAccountId, req.categoryId, req.type, req.amount, req.transactedOn, req.memo)
        return transactions.findById(user.householdId, id)!!.toResponse()
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: TransactionRequest): TransactionResponse {
        val user = currentUser()
        // 残高調整（口座の残高設定で自動登録される取引）は編集不可。直したい場合は削除して残高を設定し直す（ADR-0015）
        val existing = transactions.findById(user.householdId, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found")
        if (existing.type == "adjustment") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "残高調整の取引は編集できません")
        }
        validate(req)
        if (transactions.update(user.householdId, id, req.accountId, req.toAccountId, req.categoryId, req.type, req.amount, req.transactedOn, req.memo) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found")
        }
        return transactions.findById(user.householdId, id)!!.toResponse()
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        if (transactions.softDelete(currentUser().householdId, id) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found")
        }
    }
}
