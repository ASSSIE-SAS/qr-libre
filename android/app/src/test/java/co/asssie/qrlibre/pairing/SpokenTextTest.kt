package co.asssie.qrlibre.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTextTest {

    private fun message(
        app: String = "Banco Demo",
        title: String = "Te llegó un pago",
        body: String = "Recibiste \$50.000",
        verdict: String? = "payment",
    ) = CaptureMessage(
        id = 7,
        app = app,
        title = title,
        body = body,
        epochMs = 1_700_000_000_000,
        verdict = verdict,
    )

    @Test
    fun saysTheAppThenTheBody() {
        assertEquals(
            "Aviso de Banco Demo. Recibiste \$50.000",
            spokenText(message()),
        )
    }

    @Test
    fun fallsBackToTheTitleWhenTheBodyIsEmpty() {
        assertEquals(
            "Aviso de Banco Demo. Te llegó un pago",
            spokenText(message(body = "")),
        )
    }

    @Test
    fun saysOnlyTheHeadlineWhenThereIsNoTextAtAll() {
        assertEquals(
            "Aviso de Banco Demo.",
            spokenText(message(title = " ", body = "")),
        )
    }

    @Test
    fun usesAGenericIntroWhenTheAppNameIsBlank() {
        val text = spokenText(message(app = "  "))

        assertTrue("a blank app must not leave a dangling 'de'", text.startsWith("Aviso. "))
        assertFalse(text.contains("Aviso de ."))
    }

    @Test
    fun collapsesWhitespaceSoTheVoiceDoesNotStutter() {
        assertEquals(
            "Aviso de Banco Demo. Linea1 Linea2 fin",
            spokenText(message(body = "  Linea1\n\nLinea2\t fin  ")),
        )
    }

    @Test
    fun truncatesALongBodyAndMarksTheCut() {
        val longBody = "palabra ".repeat(40) + "FINAL"

        val text = spokenText(message(body = longBody))

        assertTrue("a long body must be cut", text.endsWith("…"))
        assertTrue("the sentence stays short enough to speak", text.length < 220)
        assertFalse("the cut must drop the tail", text.contains("FINAL"))
    }

    @Test
    fun keepsAccentsAndEmoji() {
        assertTrue(spokenText(message(body = "Café ☕ listo")).contains("Café ☕ listo"))
    }

    @Test
    fun neverClaimsAPaymentWasConfirmed() {
        for (verdict in listOf("payment", "not_payment", null)) {
            val text = spokenText(message(verdict = verdict))

            assertFalse(
                "the sentence must not add a confirmation the app cannot make: $text",
                text.contains("confirmad", ignoreCase = true),
            )
        }
    }
}
