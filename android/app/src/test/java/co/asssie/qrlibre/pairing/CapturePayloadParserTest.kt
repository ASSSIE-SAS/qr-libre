package co.asssie.qrlibre.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapturePayloadParserTest {

    private val message = CaptureMessage(
        id = 7,
        app = "Banco Demo",
        title = "Te llegó un pago",
        body = "Recibiste \$50.000",
        epochMs = 1_700_000_000_000,
        verdict = "payment",
    )

    private val encoded = encodeCaptureMessage(message)

    @Test
    fun roundTripsThePayloadTheEncoderProduces() {
        assertEquals(message, decodeCaptureMessage(encoded))
    }

    @Test
    fun roundTripsEscapedStringsAndANullVerdict() {
        val escaped = message.copy(
            app = "Banco \"Demo\"",
            title = "Línea1\nLínea2",
            body = "a\\b\tc\u0001 Café ☕",
            verdict = null,
        )

        assertEquals(escaped, decodeCaptureMessage(encodeCaptureMessage(escaped)))
    }

    @Test
    fun parsesTheKnownPayloadFromTheWireContract() {
        val known =
            "{\"id\":7,\"app\":\"Banco Demo\",\"title\":\"Te llegó un pago\"," +
                "\"body\":\"Recibiste \$50.000\",\"epoch_ms\":1700000000000," +
                "\"verdict\":\"payment\"}"

        assertEquals(message, decodeCaptureMessage(known))
    }

    @Test
    fun rejectsMalformedPayloads() {
        val malformed = listOf(
            "",
            " ",
            "null",
            "{}",
            "[]",
            "not json",
            encoded.dropLast(1),
            encoded + "x",
            encoded.replace("\"app\":", "\"appX\":"),
            encoded.replace("\"id\":7", "\"id\":\"7\""),
            encoded.replace("\"epoch_ms\":1700000000000", "\"epoch_ms\":\"1700000000000\""),
            encoded.replace("\"epoch_ms\":1700000000000", "\"epoch_ms\":1700000000000.5"),
            encoded.replace("\"verdict\":\"payment\"", "\"verdict\":7"),
            encoded.replace("\"verdict\":\"payment\"", "\"verdict\":\"unknown\""),
            encoded.replace("\"app\":\"Banco Demo\"", "\"app\":\"Banco \\q Demo\""),
            encoded.replace("\"app\":\"Banco Demo\"", "\"app\":\"Banco\u0001Demo\""),
        )

        for (payload in malformed) {
            assertNull("expected null for <$payload>", decodeCaptureMessage(payload))
        }
    }

    @Test
    fun neverThrowsOnGarbage() {
        val samples = listOf(
            "{\"id\":-}",
            "{\"id\":9223372036854775808}",
            "{\"app\":\"\\uD800\"}",
            "{\"a\":{\"b\":1}}",
            "{\"id\":1,\"app\":\"a\",\"title\":\"t\",\"body\":\"b\",\"epoch_ms\":1,\"verdict\":null,",
            "{",
            "}",
        )

        for (payload in samples) {
            assertNull(decodeCaptureMessage(payload))
        }
    }
}
