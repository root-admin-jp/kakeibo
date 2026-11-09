package jp.kakeibo.api.csv

import jp.kakeibo.api.clearAuth
import jp.kakeibo.api.db.AccountRepository
import jp.kakeibo.api.db.AccountRow
import jp.kakeibo.api.db.CategoryRepository
import jp.kakeibo.api.db.CategoryRow
import jp.kakeibo.api.db.ImportJobRepository
import jp.kakeibo.api.db.TransactionRepository
import jp.kakeibo.api.db.TransactionRow
import jp.kakeibo.api.loginAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvControllerTest {
    private val transactions: TransactionRepository = mock()
    private val accounts: AccountRepository = mock()
    private val categories: CategoryRepository = mock()
    private val importJobs: ImportJobRepository = mock()
    private val controller = CsvController(transactions, accounts, categories, importJobs)

    /** transactions.insert の引数をそのまま記録する: (householdId, accountId, toAccountId, categoryId, type, amount, date, memo) */
    private val inserted = mutableListOf<List<Any?>>()

    private val categoryRows = listOf(
        CategoryRow(10, 7, null, "食費", 0),
        CategoryRow(11, 7, 10, "食料品", 1),
        CategoryRow(20, 7, null, "給与", 2),
        CategoryRow(21, 7, 20, "給料", 3),
    )

    @BeforeEach
    fun setUp() {
        loginAs(userId = 42, householdId = 7)
        whenever(accounts.findById(7, 1)).thenReturn(AccountRow(1, 7, null, "銀行", "bank", 0))
        whenever(categories.listByHousehold(7)).thenReturn(categoryRows)
        whenever(transactions.insert(any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull()))
            .thenAnswer { inv -> inserted.add(inv.arguments.toList()); 1L }
        whenever(importJobs.insert(any(), anyOrNull(), any(), any(), anyOrNull())).thenReturn(1L)
    }

    @AfterEach
    fun tearDown() = clearAuth()

    private fun csvFile(content: String) =
        MockMultipartFile("file", "test.csv", "text/csv", content.toByteArray(Charsets.UTF_8))

    @Test
    fun `import parses MF format rows and skips bad rows`() {
        val csv = """
            日付,収支種別,金額,大項目,中項目,内容
            2026/11/01,支出,"1,200円",食費,食料品,スーパー
            2026-11-02,収入,300000,給与,給料,11月給料
            2026-11-03,支出,0,食費,食料品,金額ゼロ
            2026-11-04,振替,5000,,,口座移動
            bad-date,支出,100,食費,食料品,壊れた行
            2026-11-05,支出,800,食費,存在しない中項目,コンビニ
        """.trimIndent()

        val result = controller.import(1, null, csvFile(csv))

        assertEquals(3, result.imported)
        assertEquals(3, result.skipped)
        assertEquals(3, result.errors.size)
        assertTrue(result.errors.any { it.contains("金額が不正です") })
        assertTrue(result.errors.any { it.contains("振替には振替先口座") })

        // 1行目: 日付変換 (yyyy/MM/dd)・金額のカンマと円の除去・中項目まで解決
        val first = inserted[0]
        assertEquals(7L, first[0])
        assertEquals("expense", first[4])
        assertEquals(1200L, first[5])
        assertEquals(LocalDate.of(2026, 11, 1), first[6])
        assertEquals(11L, first[3])
        assertEquals("スーパー", first[7])
        // 2行目: 収入・給料カテゴリ
        assertEquals("income", inserted[1][4])
        assertEquals(300000L, inserted[1][5])
        assertEquals(21L, inserted[1][3])
        // 3件目（元は6行目）: 存在しない中項目は大項目にフォールバック
        assertEquals(10L, inserted[2][3])
        // 取込ジョブが done で記録される
        verify(importJobs).insert(any(), anyOrNull(), org.mockito.kotlin.eq("done"), org.mockito.kotlin.eq(3), anyOrNull())
    }

    @Test
    fun `import transfer requires and uses toAccountId`() {
        val csv = """
            日付,収支種別,金額,大項目,中項目,内容
            2026-11-04,振替,5000,,,口座移動
        """.trimIndent()
        val result = controller.import(1, 2, csvFile(csv))
        assertEquals(1, result.imported)
        assertEquals("transfer", inserted[0][4])
        assertEquals(2L, inserted[0][2])
    }

    @Test
    fun `import accepts signed adjustment rows without category`() {
        val csv = """
            日付,収支種別,金額,大項目,中項目,内容
            2026-11-06,残高調整,-1500,,,残高調整
            2026-11-07,残高調整,0,,,ゼロは不正
        """.trimIndent()
        val result = controller.import(1, null, csvFile(csv))
        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("adjustment", inserted[0][4])
        assertEquals(-1500L, inserted[0][5])
        assertEquals(null, inserted[0][3]) // 残高調整はカテゴリを持たない
    }

    @Test
    fun `import with unknown account is bad request and inserts nothing`() {
        val csv = "日付,収支種別,金額,大項目,中項目,内容\n2026-11-01,支出,100,食費,食料品,x\n"
        val e = assertFailsWith<ResponseStatusException> { controller.import(99, null, csvFile(csv)) }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
        assertEquals(0, inserted.size)
    }

    @Test
    fun `export writes header labels and category names with BOM`() {
        whenever(transactions.list(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            listOf(
                TransactionRow(1, 7, 1, null, 11, "expense", 1200, LocalDate.of(2026, 11, 1), "スーパー"),
                TransactionRow(2, 7, 1, null, 21, "income", 300000, LocalDate.of(2026, 11, 2), "給料"),
                TransactionRow(3, 7, 1, null, null, "adjustment", -2500, LocalDate.of(2026, 11, 3), "残高調整"),
            ),
        )
        val res = controller.export("2026-11")
        val body = res.body!!
        assertTrue(body.startsWith("\uFEFF"), "Excel互換のためBOM付きで返す") // 先頭は U+FEFF
        assertTrue(body.contains("日付") && body.contains("収支種別"))
        assertTrue(body.contains("支出") && body.contains("収入"))
        assertTrue(body.contains("残高調整") && body.contains("-2500")) // 残高調整は符号付きのまま書き出す
        assertTrue(body.contains("食費") && body.contains("食料品"))
        assertTrue(body.contains("スーパー"))
        assertEquals("attachment; filename=kakeibo-2026-11.csv", res.headers.getFirst("Content-Disposition"))
    }
}
