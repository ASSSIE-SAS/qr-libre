package co.asssie.qrlibre.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {

    @Test
    fun collapsesInternalWhitespaceRuns() {
        assertEquals(
            "Recibiste 35.000 de Ana",
            normalizeNotificationText("  Recibiste   35.000\t\tde   Ana  "),
        )
    }

    @Test
    fun stripsEmptyLines() {
        assertEquals(
            "Hola\nMundo",
            normalizeNotificationText("Hola\n\n   \n\t\nMundo  "),
        )
    }

    @Test
    fun keepsMeaningfulLineBreaksAndHandlesCarriageReturns() {
        assertEquals(
            "Título\nCuerpo",
            normalizeNotificationText("Título\r\nCuerpo"),
        )
        assertEquals(
            "Título\nCuerpo con espacios",
            normalizeNotificationText("  Título  \n  Cuerpo   con   espacios  "),
        )
    }

    @Test
    fun nullReturnsEmptyString() {
        assertEquals("", normalizeNotificationText(null))
    }

    @Test
    fun blankInputReturnsEmptyString() {
        assertEquals("", normalizeNotificationText("   \n\t  \n "))
        assertEquals("", normalizeNotificationText(""))
    }

    @Test
    fun keepsAccentsAndAmounts() {
        assertEquals(
            "Recibiste \$ 35.000 de José",
            normalizeNotificationText("Recibiste \$   35.000 de José"),
        )
    }

    @Test
    fun collapsesNoBreakSpaces() {
        assertEquals(
            "Recibiste 35.000 pesos",
            normalizeNotificationText("Recibiste\u00A0\u00A035.000\u202Fpesos"),
        )
    }
}
