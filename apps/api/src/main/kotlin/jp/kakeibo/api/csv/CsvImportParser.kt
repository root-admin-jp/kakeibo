package jp.kakeibo.api.csv

import java.time.LocalDate

/** パーサーが1行から取り出した取引。カテゴリ名は未解決のまま持ち、ID解決は CsvController 側で行う。 */
data class ParsedCsvRow(
    val date: LocalDate,
    val type: String,
    val amount: Long,
    val topCategory: String,
    val childCategory: String,
    val memo: String?,
)

/**
 * CSV取り込み形式のパーサー（Phase 2 Step 0）。
 * 機関別形式はこのインターフェースを実装して [CsvImportParsers] に登録すれば追加できる。
 * parse は行単位の検証も行い、不正な行は例外（メッセージはそのまま取り込みエラーになる）を投げる。
 */
interface CsvImportParser {
    /** 形式の識別子（例: mf） */
    val id: String

    /** その行がこの形式のヘッダー行かどうか（形式判定の署名も兼ねる） */
    fun isHeader(row: Array<String>): Boolean

    fun parse(row: Array<String>): ParsedCsvRow
}

/**
 * 汎用/MF形式: 日付, 収支種別（収入/振替/それ以外は支出）, 金額, 大項目, 中項目, 内容。
 * 収支種別「残高調整」（ADR-0015）は金額が符号付きで、カテゴリを持たない。
 */
object MfCsvImportParser : CsvImportParser {
    override val id = "mf"

    override fun isHeader(row: Array<String>) = row.getOrNull(0)?.trim() == "日付"

    override fun parse(row: Array<String>): ParsedCsvRow {
        val date = LocalDate.parse(row[0].trim().replace("/", "-"))
        val type = when (row[1].trim()) {
            "収入" -> "income"; "振替" -> "transfer"; "残高調整" -> "adjustment"; else -> "expense"
        }
        val amount = row[2].trim().replace(",", "").replace("円", "").toLong()
        // 残高調整だけは符号付き（マイナス可・ゼロ不可）、他は正の金額のみ
        if (if (type == "adjustment") amount == 0L else amount <= 0) {
            throw IllegalArgumentException("金額が不正です")
        }
        return ParsedCsvRow(
            date = date,
            type = type,
            amount = amount,
            topCategory = row.getOrNull(3)?.trim() ?: "",
            childCategory = row.getOrNull(4)?.trim() ?: "",
            memo = row.getOrNull(5)?.trim()?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * 登録済みパーサーの一覧と選択。先頭行のヘッダー署名で形式を自動判定し、
 * どの署名にも一致しなければデフォルト（MF形式）として扱う（ヘッダーなしCSVも従来どおり取り込める）。
 */
object CsvImportParsers {
    private val parsers: List<CsvImportParser> = listOf(MfCsvImportParser)
    val default: CsvImportParser = MfCsvImportParser

    fun select(firstRow: Array<String>): CsvImportParser =
        parsers.firstOrNull { it.isHeader(firstRow) } ?: default
}
