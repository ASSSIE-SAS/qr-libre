package co.asssie.qrlibre.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMessageTest {

    private val secret = ByteArray(16) { it.toByte() }

    /** One fixed message shared by the known-answer and frame assertions. */
    private val message = CaptureMessage(
        id = 7,
        app = "Banco Demo",
        title = "Te llegó un pago",
        body = "Recibiste \$50.000",
        epochMs = 1_700_000_000_000,
        verdict = "payment",
    )

    // Produced independently with HMAC-SHA256 over the exact payload bytes.
    private val knownPayload =
        "{\"id\":7,\"app\":\"Banco Demo\",\"title\":\"Te llegó un pago\"," +
            "\"body\":\"Recibiste \$50.000\",\"epoch_ms\":1700000000000," +
            "\"verdict\":\"payment\"}"
    private val knownSignature = "v1:TPWcP_JPRFU1GZPpVbb4iQl7V__gTvgF3TYMxnnRTs0"

    @Test
    fun encodeUsesCompactJsonWithTheStableKeyOrder() {
        assertEquals(knownPayload, encodeCaptureMessage(message))
    }

    @Test
    fun encodeEscapesStringsLikeTheExportBuilder() {
        val escaped = CaptureMessage(
            id = -3,
            app = "Banco \"Demo\"",
            title = "Línea1\nLínea2",
            body = "a\\b\tc\u0001",
            epochMs = 0,
            verdict = null,
        )

        assertEquals(
            "{\"id\":-3,\"app\":\"Banco \\\"Demo\\\"\"," +
                "\"title\":\"Línea1\\nLínea2\"," +
                "\"body\":\"a\\\\b\\tc\\u0001\"," +
                "\"epoch_ms\":0,\"verdict\":null}",
            encodeCaptureMessage(escaped),
        )
    }

    @Test
    fun encodeKeepsAccentsAndEmojiVerbatim() {
        val accented = message.copy(app = "Café ☕", title = "Aviso", body = "Sí")

        assertTrue(encodeCaptureMessage(accented).contains("\"app\":\"Café ☕\""))
    }

    @Test
    fun encodeWritesTheVerdictAsNullOrAQuotedWireToken() {
        assertTrue(encodeCaptureMessage(message.copy(verdict = null)).endsWith("\"verdict\":null}"))
        assertTrue(
            encodeCaptureMessage(message.copy(verdict = "not_payment"))
                .endsWith("\"verdict\":\"not_payment\"}"),
        )
    }

    @Test
    fun signMatchesTheKnownAnswer() {
        assertEquals(knownSignature, signCaptureMessage(knownPayload, secret))
    }

    @Test
    fun signIsUnpaddedBase64Url() {
        val token = signCaptureMessage(knownPayload, secret).removePrefix("v1:")

        assertFalse("base64url never pads", token.contains('='))
        assertTrue("only the base64url alphabet appears", token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun signedFrameIsPayloadNewlineSignature() {
        assertEquals("$knownPayload\n$knownSignature", signedFrame(message, secret))
    }

    @Test
    fun verifyReturnsThePayloadForAValidFrame() {
        assertEquals(knownPayload, verifyFrame(signedFrame(message, secret), secret))
    }

    @Test
    fun verifyRejectsAWrongSecret() {
        val frame = signedFrame(message, secret)
        val other = ByteArray(16) { (it + 1).toByte() }

        assertNull(verifyFrame(frame, other))
    }

    @Test
    fun verifyRejectsTamperedPayloads() {
        val frame = signedFrame(message, secret)
        val tampered = frame.replace("\$50.000", "\$500.000")

        assertNull(verifyFrame(tampered, secret))
    }

    @Test
    fun verifyRejectsMalformedFrames() {
        val payload = encodeCaptureMessage(message)
        val signature = signCaptureMessage(payload, secret)
        val malformed = listOf(
            "",
            payload,
            payload + "\n",
            payload + "\nnot-a-signature",
            payload + "\nv2:" + signature.removePrefix("v1:"),
            // A truncated HMAC can never match a full one.
            payload + "\n" + signature.dropLast(4),
            // Trailing junk changes the compared token.
            payload + "\n" + signature + "x",
            // An extra newline is part of the signature, not a second field.
            payload + "\n" + signature + "\n",
            "garbage\n" + signature,
        )

        for (frame in malformed) {
            assertNull("expected null for <$frame>", verifyFrame(frame, secret))
        }
    }

    @Test
    fun verifyNeverThrowsOnAnUnusableSecret() {
        assertNull(verifyFrame(signedFrame(message, secret), ByteArray(0)))
    }
}
