package jp.kakeibo.api.category

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.CategoryRow
import jp.kakeibo.api.security.currentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class CategoryRequest(val parentId: Long?, @field:NotBlank val name: String, val sortOrder: Int = 0)
data class CategoryResponse(val id: Long, val parentId: Long?, val name: String, val sortOrder: Int)

fun CategoryRow.toResponse() = CategoryResponse(id, parentId, name, sortOrder)

@RestController
@RequestMapping("/api/categories")
class CategoryController(private val categories: CategoryRepository) {
    @GetMapping
    fun list(): List<CategoryResponse> = categories.listByHousehold(currentUser().householdId).map { it.toResponse() }

    @PostMapping
    fun create(@Valid @RequestBody req: CategoryRequest): CategoryResponse {
        val user = currentUser()
        if (req.parentId != null && categories.findById(user.householdId, req.parentId) == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "parent category not found")
        }
        val id = categories.insert(user.householdId, req.parentId, req.name, req.sortOrder)
        return categories.findById(user.householdId, id)!!.toResponse()
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        if (categories.delete(currentUser().householdId, id) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "category not found")
        }
    }
}
