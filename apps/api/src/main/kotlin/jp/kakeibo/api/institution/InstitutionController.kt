package jp.kakeibo.api.institution

import jp.kakeibo.api.db.InstitutionRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class InstitutionResponse(val id: Long, val code: String, val name: String, val kind: String)

@RestController
@RequestMapping("/api/institutions")
class InstitutionController(private val institutions: InstitutionRepository) {
    @GetMapping
    fun list(): List<InstitutionResponse> = institutions.listAll().map { InstitutionResponse(it.id, it.code, it.name, it.kind) }
}
