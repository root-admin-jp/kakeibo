package jp.kakeibo.api.db

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MyBatis Mapper の対MySQLテスト。Testcontainers で実MySQL 8を起動し、
 * Flyway DDL を適用した上で Repository 経由の読み書きを検証する。
 * 各テストは @Transactional でロールバックされ、互いに干渉しない（マスタデータは残る）。
 */
@SpringBootTest
@Testcontainers
@Transactional
class MapperIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.0.36")
            .withDatabaseName("kakeibo")
            .withUsername("kakeibo")
            .withPassword("kakeibo")
            .withCommand("--default-authentication-plugin=mysql_native_password")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            // テスト実行ディレクトリ (apps/api) から見た DDL の場所を絶対パスで明示する
            val ddl = Paths.get(System.getProperty("user.dir"), "../../db/migration").normalize().toString()
            registry.add("spring.flyway.locations") { "filesystem:$ddl" }
            registry.add("kakeibo.jwt-secret") { "test-secret-test-secret-test-secret-0123456789" }
        }
    }

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var households: HouseholdRepository
    @Autowired lateinit var accounts: AccountRepository
    @Autowired lateinit var categories: CategoryRepository
    @Autowired lateinit var transactions: TransactionRepository
    @Autowired lateinit var institutions: InstitutionRepository
    @Autowired lateinit var importJobs: ImportJobRepository

    private var seq = 0
    private fun newHousehold(): Long {
        seq++
        val uid = users.insert("mapper-test-$seq-${System.nanoTime()}@example.com", "テスト")
        return households.insert(uid, "テスト家計")
    }

    @Test
    fun `household roundtrip and unknown owner`() {
        val uid = users.insert("owner-${System.nanoTime()}@example.com", "山田")
        assertTrue(uid > 0)
        val hid = households.insert(uid, "山田の家計")
        assertTrue(hid > 0)
        assertEquals(hid, households.findIdByOwner(uid))
        assertNull(households.findIdByOwner(uid + 999_999)) // 存在しないオーナーは null
        assertNull(users.findByEmail("nobody-${System.nanoTime()}@example.com"))
    }

    @Test
    fun `inserted user can be found by email`() {
        val email = "findme-${System.nanoTime()}@example.com"
        val uid = users.insert(email, "探す")
        val found = users.findByEmail(email)
        assertNotNull(found)
        assertEquals(uid, found.id)
        assertEquals("探す", found.displayName)
    }

    @Test
    fun `institution master seed has 8 rows`() {
        val all = institutions.listAll()
        assertEquals(8, all.size)
        val codes = all.map { it.code }.toSet()
        assertTrue("aeon_bank" in codes && "mizuho_bank" in codes && "mobile_suica" in codes && "sbi_securities" in codes)
    }

    @Test
    fun `account balance aggregates income expense and transfers`() {
        val hid = newHousehold()
        val a = accounts.insert(hid, null, "銀行", "bank")
        val b = accounts.insert(hid, null, "財布", "cash")
        val nov = LocalDate.of(2026, 11, 10)
        transactions.insert(hid, a, null, null, "income", 10_000, nov, null)
        transactions.insert(hid, a, null, null, "expense", 3_000, nov, null)
        transactions.insert(hid, a, b, null, "transfer", 2_000, nov, null)

        val list = accounts.listByHousehold(hid).associateBy { it.id }
        assertEquals(5_000L, list[a]?.balance)
        assertEquals(2_000L, list[b]?.balance)
    }

    @Test
    fun `transaction filtering soft delete and tenant isolation`() {
        val hid = newHousehold()
        val other = newHousehold()
        val acc = accounts.insert(hid, null, "銀行", "bank")
        val otherAcc = accounts.insert(other, null, "銀行", "bank")
        val cat = categories.insert(hid, null, "食費", 0)

        val novTx = transactions.insert(hid, acc, null, cat, "expense", 1000, LocalDate.of(2026, 11, 5), "11月")
        transactions.insert(hid, acc, null, null, "expense", 2000, LocalDate.of(2026, 10, 5), "10月")
        transactions.insert(other, otherAcc, null, null, "expense", 9999, LocalDate.of(2026, 11, 5), "他人の取引")

        val novFrom = LocalDate.of(2026, 11, 1)
        val novTo = LocalDate.of(2026, 11, 30)
        val novList = transactions.list(hid, novFrom, novTo, null, null)
        assertEquals(1, novList.size)
        assertEquals(novTx, novList[0].id)
        assertEquals(1, transactions.list(hid, novFrom, novTo, acc, null).size)
        assertEquals(1, transactions.list(hid, novFrom, novTo, null, cat).size)
        assertEquals(0, transactions.list(hid, novFrom, novTo, otherAcc, null).size)
        // テナント分離: 他人の口座・取引は自テナントの検索で見えない
        assertNull(accounts.findById(hid, otherAcc))
        assertNull(transactions.findById(hid, transactions.list(other, novFrom, novTo, null, null)[0].id))

        // 論理削除後は一覧・検索から消える
        assertEquals(1, transactions.softDelete(hid, novTx))
        assertEquals(0, transactions.list(hid, novFrom, novTo, null, null).size)
        assertNull(transactions.findById(hid, novTx))
        assertEquals(0, transactions.softDelete(hid, novTx)) // 二重削除は0件
    }

    @Test
    fun `monthly totals and top category aggregation`() {
        val hid = newHousehold()
        val acc = accounts.insert(hid, null, "銀行", "bank")
        val parent = categories.insert(hid, null, "食費", 0)
        val child1 = categories.insert(hid, parent, "食料品", 1)
        val child2 = categories.insert(hid, parent, "外食", 2)
        val nov = LocalDate.of(2026, 11, 10)
        transactions.insert(hid, acc, null, child1, "expense", 1000, nov, null)
        transactions.insert(hid, acc, null, child2, "expense", 500, nov, null)
        transactions.insert(hid, acc, null, null, "expense", 300, nov, "未分類")
        transactions.insert(hid, acc, null, null, "income", 5000, nov, null)
        transactions.insert(hid, acc, null, null, "income", 9999, LocalDate.of(2026, 10, 1), "前月")

        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        val (income, expense) = transactions.monthlyTotals(hid, from, to)
        assertEquals(5000L, income)
        assertEquals(1800L, expense)

        val byCategory = transactions.totalsByTopCategory(hid, from, to, "expense").toMap()
        assertEquals(1500L, byCategory["食費"]) // 子カテゴリは親に集約
        assertEquals(300L, byCategory["未分類"])
    }

    @Test
    fun `category hierarchy order and import job insert`() {
        val hid = newHousehold()
        val parent = categories.insert(hid, null, "交通費", 5)
        val child = categories.insert(hid, parent, "電車", 6)
        val list = categories.listByHousehold(hid)
        assertEquals(listOf(parent, child), list.map { it.id })
        assertEquals(parent, categories.findById(hid, child)?.parentId)

        val jobId = importJobs.insert(hid, null, "done", 12, null)
        assertTrue(jobId > 0)
    }
}
