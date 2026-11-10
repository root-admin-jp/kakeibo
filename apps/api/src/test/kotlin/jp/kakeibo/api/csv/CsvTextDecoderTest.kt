package jp.kakeibo.api.csv

import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

class CsvTextDecoderTest {
    private val sample = "日付,収支種別,金額\n2026-11-01,支出,1200\n"

    @Test
    fun `decodes plain UTF-8`() {
        assertEquals(sample, CsvTextDecoder.decode(sample.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `decodes UTF-8 with BOM and strips it`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(sample, CsvTextDecoder.decode(bom + sample.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `decodes Shift_JIS CP932`() {
        val bytes = sample.toByteArray(Charset.forName("windows-31j"))
        assertEquals(sample, CsvTextDecoder.decode(bytes))
    }

    @Test
    fun `decodes ASCII as UTF-8`() {
        val ascii = "date,type,amount\n2026-11-01,expense,1200\n"
        assertEquals(ascii, CsvTextDecoder.decode(ascii.toByteArray(Charsets.US_ASCII)))
    }
}
