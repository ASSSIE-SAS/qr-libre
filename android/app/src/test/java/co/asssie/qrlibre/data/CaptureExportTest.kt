package co.asssie.qrlibre.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureExportTest {

    private fun capture(
        packageName: String = "co.banco.uno",
        app: String = "Banco Uno",
        title: String = "Aviso",
        body: String = "Cuerpo",
        epochMs: Long = 1_700_000_000_000L,
    ) = Capture(
        id = 1L,
        packageName = packageName,
        app = app,
        title = title,
        body = body,
        epochMs = epochMs,
    )

    @Test
    fun emptyHistoryIsAnEmptyJsonArray() {
        assertEquals("[]", exportCaptures(emptyList()))
    }

    @Test
    fun oneCaptureIsExactJsonWithStableKeyOrder() {
        val json = exportCaptures(
            listOf(
                capture(
                    packageName = "co.test",
                    app = "App",
                    title = "Dijo \"hola\" \\ fin",
                    body = "Línea 1\nLínea 2\ttab",
                    epochMs = 1_700_000_000_000L,
                ),
            ),
        )

        val expected = "[\n" +
            "  {\"package\":\"co.test\",\"app\":\"App\"," +
            "\"title\":\"Dijo \\\"hola\\\" \\\\ fin\"," +
            "\"body\":\"Línea 1\\nLínea 2\\ttab\"," +
            "\"epoch_ms\":1700000000000}\n" +
            "]"
        assertEquals(expected, json)

        val keys = listOf("\"package\"", "\"app\"", "\"title\"", "\"body\"", "\"epoch_ms\"")
        val positions = keys.map { json.indexOf(it) }
        assertTrue("every key must be present", positions.all { it >= 0 })
        assertEquals("keys must stay in the documented order", positions.sorted(), positions)
    }

    @Test
    fun quotesBackslashesNewlinesAndTabsAreEscaped() {
        val json = exportCaptures(
            listOf(capture(title = "\"comillas\"", body = "a\\b\r\nc\td")),
        )

        assertTrue("quotes are escaped", json.contains("\\\"comillas\\\""))
        assertTrue("a backslash is escaped", json.contains("a\\\\b"))
        assertTrue("carriage return is escaped", json.contains("a\\\\b\\r\\nc"))
        assertTrue("tab is escaped", json.contains("c\\td"))
        assertFalse("no raw tab leaks into the document", json.contains('\t'))
        // Two structural line breaks plus the object row: escapes never add lines.
        assertEquals(3, json.lines().size)
    }

    @Test
    fun otherControlCharactersUseUnicodeEscapes() {
        val json = exportCaptures(listOf(capture(body = "a\u0001b\u0000c")))

        assertTrue(json.contains("a\\u0001b\\u0000c"))
    }

    @Test
    fun unicodeAccentsCurrencyAndEmojiArePreserved() {
        val title = "Llegó un pago de \$1.250 💸"
        val body = "Café ☕ — ñandú\n\nSegunda línea 😀"
        val json = exportCaptures(listOf(capture(title = title, body = body)))

        assertTrue("title must keep its raw characters", json.contains(title))
        assertFalse("emoji must not be escaped", json.contains("\\ud83d"))
        assertTrue("body must keep accents and the em dash", json.contains("Café ☕ — ñandú"))
        assertTrue("blank lines stay as escaped newlines", json.contains("ñandú\\n\\nSegunda"))
    }

    @Test
    fun bodyWithBlankLinesAndEdgeBreaksIsPreservedExactly() {
        val body = "\nPrimera línea\n\nTercera línea\n\n\n"
        val json = exportCaptures(listOf(capture(body = body)))

        assertTrue(json.contains("\"body\":\"\\nPrimera línea\\n\\nTercera línea\\n\\n\\n\""))
    }

    @Test
    fun givenOrderIsKeptBecauseItIsAlreadyNewestFirst() {
        val json = exportCaptures(
            listOf(
                capture(packageName = "primera", epochMs = 3_000L),
                capture(packageName = "segunda", epochMs = 2_000L),
                capture(packageName = "tercera", epochMs = 1_000L),
            ),
        )

        val first = json.indexOf("primera")
        val second = json.indexOf("segunda")
        val third = json.indexOf("tercera")
        assertTrue(first in 0 until second)
        assertTrue(second in 0 until third)
    }

    @Test
    fun fileNameUsesTheLocalDateOfTheTimestamp() {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 28, 9, 41, 7)
        }

        assertEquals(
            "qr-libre-avisos-2026-11-28.json",
            exportFileName(calendar.timeInMillis),
        )
    }
}
