package co.asssie.qrlibre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCopyTest {

    private val header = "Banco Uno\nco.banco.uno\n09:41:07 28/11/2026"

    private fun copy(
        title: String,
        body: String,
        app: String = "Banco Uno",
        packageName: String = "co.banco.uno",
        timestamp: String = "09:41:07 28/11/2026",
    ): String = captureCopyText(
        app = app,
        packageName = packageName,
        title = title,
        body = body,
        timestamp = timestamp,
    )

    @Test
    fun longBodyKeepsInternalBlankLinesAndEdgeBreaksVerbatim() {
        val body = "\nPrimera línea con ñ y tildes\n\n" +
            "Tercera línea  con  espacios dobles\n\n\n" +
            "Cierre sin punto\n"

        val text = copy(title = "Aviso de pago", body = body)

        val expected = "$header\n\nAviso de pago\n\n$body"
        assertEquals(expected, text)
        assertTrue("the body must be appended verbatim", text.endsWith(body))
    }

    @Test
    fun emptyTitleAndBodyProduceOnlyTheHeader() {
        assertEquals(header, copy(title = "", body = ""))
    }

    @Test
    fun oneEmptySectionAddsNoPhantomSeparator() {
        assertEquals("$header\n\nSolo cuerpo", copy(title = "", body = "Solo cuerpo"))
        assertEquals("$header\n\nSolo título", copy(title = "Solo título", body = ""))
    }

    @Test
    fun unicodeAccentsCurrencyAndEmojiArePreserved() {
        val title = "Llegó un pago de \$1.250 💸"
        val body = "Café ☕ — ñandú\n\nSegunda línea 😀"

        val text = copy(title = title, body = body)

        assertTrue("title must keep accents, currency and emoji", text.contains(title))
        assertTrue("body must keep accents, currency and emoji", text.contains(body))
    }

    @Test
    fun headerListsAppPackageAndTimestampOnce() {
        val packageName = "com.nequi.MobileApp"
        val text = copy(
            title = "Título",
            body = "Cuerpo",
            app = "Nequi",
            packageName = packageName,
            timestamp = "08:05:00 01/01/2027",
        )

        val headerLines = text.substringBefore("\n\n").lines()
        assertEquals(
            listOf("Nequi", packageName, "08:05:00 01/01/2027"),
            headerLines,
        )
        assertEquals(1, text.lineSequence().count { it == packageName })
    }
}
