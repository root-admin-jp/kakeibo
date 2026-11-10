package jp.kakeibo.api.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * アップロードされたCSVのバイト列をテキストに変換する（Phase 2 Step 0）。
 * 判定順: UTF-8 BOM → 厳密なUTF-8としてデコードできるか → Shift_JIS(CP932)。
 * 銀行系CSVに多い Shift_JIS を、文字コード指定なしで取り込めるようにするための土台。
 */
object CsvTextDecoder {
    private val cp932 = charset("windows-31j")

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        val text = tryDecodeUtf8(bytes) ?: String(bytes, cp932)
        return text.removePrefix("\uFEFF")
    }

    private fun tryDecodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }
}
