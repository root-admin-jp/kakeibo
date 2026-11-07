package jp.kakeibo.api.institution

import java.time.LocalDate

/** ADR-0006: 金融機関連携の差し替え可能な境界。Phase 1 は CsvInstitutionConnector / Mock のみ。 */
interface InstitutionConnector {
    val connectorType: String
    fun fetchTransactions(connectionRef: String, from: LocalDate, to: LocalDate): List<ImportedTransaction>
}

data class ImportedTransaction(
    val transactedOn: LocalDate,
    val amount: Long,
    val memo: String?,
    val externalId: String?,
)
