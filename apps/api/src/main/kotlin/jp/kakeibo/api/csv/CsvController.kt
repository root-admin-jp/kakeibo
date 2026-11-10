package jp.kakeibo.api.csv

import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriter
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.ImportJobRepository
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.security.currentUser
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.StringReader
import java.io.StringWriter
import java.time.YearMonth

data class ImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

/**
 * CSV取り込み（Phase 2 Step 0）。
 * 文字コードは [CsvTextDecoder] が UTF-8（BOM含む）／Shift_JIS(CP932) を自動判定する。
 * 形式は [CsvImportParsers] が先頭行のヘッダー署名で選ぶ。現状は汎用/MF形式のみ:
 * 日付, 収支種別（収入/振替/それ以外は支出）, 金額, 大項目, 中項目, 内容
 * 既存の MF(SSNB) CSV Importer の列仕様と互換にする（要件定義 §3.3）。
 * 拡張として収支種別「残高調整」（ADR-0015）も扱う: 金額は符号付きで往復でき、カテゴリは持たない。
 */
@RestController
@RequestMapping("/api/transactions")
class CsvController(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val importJobs: ImportJobRepository,
) {
    @GetMapping("/export.csv", produces = ["text/csv"])
    fun export(@RequestParam month: String): ResponseEntity<String> {
        val user = currentUser()
        val ym = YearMonth.parse(month)
        val rows = transactions.list(user.householdId, ym.atDay(1), ym.atEndOfMonth(), null, null)
        val cats = categories.listByHousehold(user.householdId).associateBy { it.id }
        val sw = StringWriter()
        CSVWriter(sw).use { writer ->
            writer.writeNext(arrayOf("日付", "収支種別", "金額", "大項目", "中項目", "内容"))
            for (t in rows) {
                val child = t.categoryId?.let { cats[it] }
                val parent = child?.parentId?.let { cats[it] }
                val typeLabel = when (t.type) { "income" -> "収入"; "transfer" -> "振替"; "adjustment" -> "残高調整"; else -> "支出" }
                writer.writeNext(
                    arrayOf(
                        t.transactedOn.toString(), typeLabel, t.amount.toString(),
                        parent?.name ?: child?.name ?: "", if (parent != null) child?.name ?: "" else "",
                        t.memo ?: "",
                    ),
                )
            }
        }
        // Excel互換のためBOM付きUTF-8で返す
        val body = "﻿" + sw.toString()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=kakeibo-$month.csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(body)
    }

    @PostMapping("/import", consumes = ["multipart/form-data"])
    fun import(
        @RequestParam accountId: Long,
        @RequestParam(required = false) toAccountId: Long?,
        @RequestParam file: MultipartFile,
    ): ImportResult {
        val user = currentUser()
        if (accounts.findById(user.householdId, accountId) == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "account not found")
        }
        val cats = categories.listByHousehold(user.householdId)
        fun resolveCategory(top: String, child: String): Long? {
            if (top.isBlank()) return null
            val parent = cats.firstOrNull { it.parentId == null && it.name == top } ?: return null
            if (child.isBlank()) return parent.id
            return cats.firstOrNull { it.parentId == parent.id && it.name == child }?.id ?: parent.id
        }
        var imported = 0; var skipped = 0; val errors = mutableListOf<String>()
        try {
            val reader = CSVReaderBuilder(StringReader(CsvTextDecoder.decode(file.bytes))).build()
            var lineNo = 0
            var parser: CsvImportParser? = null
            for (row in reader) {
                lineNo++
                if (row.isEmpty() || row.all { it.isBlank() }) continue
                val active = parser ?: CsvImportParsers.select(row).also { parser = it }
                if (lineNo == 1 && active.isHeader(row)) continue // ヘッダー
                try {
                    val parsed = active.parse(row)
                    if (parsed.type == "transfer" && toAccountId == null) {
                        skipped++; errors.add("${lineNo}行目: 振替には振替先口座(toAccountId)が必要です"); continue
                    }
                    transactions.insert(
                        user.householdId, accountId, if (parsed.type == "transfer") toAccountId else null,
                        if (parsed.type == "adjustment") null else resolveCategory(parsed.topCategory, parsed.childCategory),
                        parsed.type, parsed.amount, parsed.date, parsed.memo,
                    )
                    imported++
                } catch (e: Exception) {
                    skipped++; errors.add("${lineNo}行目: ${e.message}")
                }
            }
            importJobs.insert(user.householdId, accountId, "done", imported, errors.takeIf { it.isNotEmpty() }?.joinToString("\n")?.take(1000))
        } catch (e: Exception) {
            importJobs.insert(user.householdId, accountId, "failed", imported, e.message?.take(1000))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CSVの読み込みに失敗しました: ${e.message}")
        }
        return ImportResult(imported, skipped, errors)
    }
}
