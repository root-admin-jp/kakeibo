package jp.kakeibo.api.account

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.AccountRow
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.security.currentUser
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneId

/**
 * currentBalance: 設定したい「現在の残高」（任意、未指定なら残高は変更しない）。
 * recordAdjustment: true なら差額を「残高調整」の取引として記録し、
 * false なら取引に残さず口座の balance_offset に保持する（ADR-0015）。
 */
data class AccountRequest(
    val institutionId: Long?,
    @field:NotBlank val name: String,
    @field:NotBlank val kind: String,
    val currentBalance: Long? = null,
    val recordAdjustment: Boolean = true,
)
data class AccountResponse(val id: Long, val institutionId: Long?, val name: String, val kind: String, val balance: Long)

fun AccountRow.toResponse() = AccountResponse(id, institutionId, name, kind, balance)

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
) {
    companion object {
        val KINDS = setOf("cash", "bank", "credit_card", "e_money", "securities", "other")
        val JST: ZoneId = ZoneId.of("Asia/Tokyo")
        const val ADJUSTMENT_MEMO = "残高調整"
    }

    @GetMapping
    fun list(): List<AccountResponse> = accounts.listByHousehold(currentUser().householdId).map { it.toResponse() }

    @PostMapping
    @Transactional
    fun create(@Valid @RequestBody req: AccountRequest): AccountResponse {
        if (req.kind !in KINDS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kind: ${req.kind}")
        val user = currentUser()
        val id = accounts.insert(user.householdId, req.institutionId, req.name, req.kind)
        // 作成直後の算出残高は 0 なので、設定値そのものが差額になる
        if (req.currentBalance != null) applyBalance(user.householdId, id, 0, req.currentBalance, req.recordAdjustment)
        return accounts.findById(user.householdId, id)!!.toResponse()
    }

    @PutMapping("/{id}")
    @Transactional
    fun update(@PathVariable id: Long, @Valid @RequestBody req: AccountRequest): AccountResponse {
        if (req.kind !in KINDS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kind: ${req.kind}")
        val user = currentUser()
        if (accounts.update(user.householdId, id, req.institutionId, req.name, req.kind) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        }
        if (req.currentBalance != null) {
            val current = accounts.findById(user.householdId, id)!!.balance
            applyBalance(user.householdId, id, current, req.currentBalance, req.recordAdjustment)
        }
        return accounts.findById(user.householdId, id)!!.toResponse()
    }

    /** 算出残高 current から target へ寄せる。差額ゼロなら何もしない。マイナス残高も許可する。 */
    private fun applyBalance(householdId: Long, accountId: Long, current: Long, target: Long, record: Boolean) {
        val delta = target - current
        if (delta == 0L) return
        if (record) {
            transactions.insert(householdId, accountId, null, null, "adjustment", delta, LocalDate.now(JST), ADJUSTMENT_MEMO)
        } else {
            accounts.addBalanceOffset(householdId, accountId, delta)
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        val user = currentUser()
        if (accounts.countTransactions(id) > 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "account has transactions")
        }
        if (accounts.delete(user.householdId, id) == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
    }
}
