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
import java.io.InputStreamReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth

data class ImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

/**
 * 汎用/MF形式CSV: 日付, 収支種別（収入/振替/それ以外は支出）, 金額, 大項目, 中項目, 内容
 * 既存の MF(SSNB) CSV Importer の列仕様と互換にする（要件定義 §3.3）。
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
                val typeLabel = when (t.type) { "income" -> "収入"; "transfer" -> "振替"; else -> "支出" }
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
            val reader = CSVReaderBuilder(InputStreamReader(file.inputStream, StandardCharsets.UTF_8)).build()
            var lineNo = 0
            for (row in reader) {
                lineNo++
                if (row.isEmpty() || row.all { it.isBlank() }) continue
                if (lineNo == 1 && row[0].trim() == "日付") continue // ヘッダー
                try {
                    val date = LocalDate.parse(row[0].trim().replace("/", "-"))
                    val type = when (row[1].trim()) { "収入" -> "income"; "振替" -> "transfer"; else -> "expense" }
                    val amount = row[2].trim().replace(",", "").replace("円", "").toLong()
                    if (amount <= 0) { skipped++; errors.add("${lineNo}行目: 金額が不正です"); continue }
                    if (type == "transfer" && toAccountId == null) {
                        skipped++; errors.add("${lineNo}行目: 振替には振替先口座(toAccountId)が必要です"); continue
                    }
                    val top = row.getOrNull(3)?.trim() ?: ""
                    val child = row.getOrNull(4)?.trim() ?: ""
                    val memo = row.getOrNull(5)?.trim()?.takeIf { it.isNotBlank() }
                    transactions.insert(
                        user.householdId, accountId, if (type == "transfer") toAccountId else null,
                        resolveCategory(top, child), type, amount, date, memo,
                    )
                    imported++
                } catch (e: Exception) {
                    skipped++; errors.add("${lineNo}行目: ${e.message}")
                }
            }
            importJobs.insert(user.householdId, accountId, "done", imported, errors.takeIf { it.isNotEmpty() }?.joinToString("
")?.take(1000))
        } catch (e: Exception) {
            importJobs.insert(user.householdId, accountId, "failed", imported, e.message?.take(1000))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CSVの読み込みに失敗しました: ${e.message}")
        }
        return ImportResult(imported, skipped, errors)
    }
}
