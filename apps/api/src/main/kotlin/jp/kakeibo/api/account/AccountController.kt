package jp.kakeibo.api.account

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.AccountRow
import jp.kakeibo.api.security.currentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class AccountRequest(val institutionId: Long?, @field:NotBlank val name: String, @field:NotBlank val kind: String)
data class AccountResponse(val id: Long, val institutionId: Long?, val name: String, val kind: String, val balance: Long)

fun AccountRow.toResponse() = AccountResponse(id, institutionId, name, kind, balance)

@RestController
@RequestMapping("/api/accounts")
class AccountController(private val accounts: AccountRepository) {
    companion object { val KINDS = setOf("cash", "bank", "credit_card", "e_money", "securities", "other") }

    @GetMapping
    fun list(): List<AccountResponse> = accounts.listByHousehold(currentUser().householdId).map { it.toResponse() }

    @PostMapping
    fun create(@Valid @RequestBody req: AccountRequest): AccountResponse {
        if (req.kind !in KINDS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kind: ${req.kind}")
        val user = currentUser()
        val id = accounts.insert(user.householdId, req.institutionId, req.name, req.kind)
        return accounts.findById(user.householdId, id)!!.toResponse()
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: AccountRequest): AccountResponse {
        if (req.kind !in KINDS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kind: ${req.kind}")
        val user = currentUser()
        if (accounts.update(user.householdId, id, req.institutionId, req.name, req.kind) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        }
        return accounts.findById(user.householdId, id)!!.toResponse()
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
