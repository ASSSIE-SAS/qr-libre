package co.asssie.qrlibre.pairing

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadTest {

    private val serviceUuid = "0000ffe0-0000-1000-8000-00805f9b34fb"

    /** A fixed secret: the bytes 0x00..0x0f, so the wire form is stable. */
    private fun fixedSecret(): ByteArray = ByteArray(16) { it.toByte() }

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun payload(
        deviceName: String = "Café Central",
        secret: ByteArray = fixedSecret(),
    ) = PairingPayload(
        deviceName = deviceName,
        secret = secret,
        serviceUuid = serviceUuid,
    )

    @Test
    fun roundTripKeepsAnAccentedNameWithSpaces() {
        val original = payload(deviceName = "Café Central")

        val decoded = decodePairingPayload(original.encode())

        assertEquals(original, decoded)
        assertEquals("Café Central", decoded?.deviceName)
    }

    @Test
    fun encodeIsDeterministicForAFixedSecret() {
        assertEquals(
            "qrlibre:1:Q2Fmw6kgQ2VudHJhbA:AAECAwQFBgcICQoLDA0ODw:" +
                "0000ffe0-0000-1000-8000-00805f9b34fb",
            payload().encode(),
        )
    }

    @Test
    fun decodeRejectsAnUnknownVersion() {
        val raw = payload().encode().replaceFirst("qrlibre:1:", "qrlibre:2:")

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsAWrongPrefix() {
        val raw = payload().encode().replaceFirst("qrlibre:", "otro:")

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsAFifteenByteSecret() {
        val raw = "qrlibre:1:${b64url("Sala".toByteArray())}:" +
            "${b64url(ByteArray(15))}:$serviceUuid"

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsASeventeenByteSecret() {
        val raw = "qrlibre:1:${b64url("Sala".toByteArray())}:" +
            "${b64url(ByteArray(17))}:$serviceUuid"

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsMalformedBase64Url() {
        val unreadableName = "qrlibre:1:no*es-base64!!:AAECAwQFBgcICQoLDA0ODw:$serviceUuid"
        val unreadableSecret = "qrlibre:1:U2FsYQ:A*ECAwQFBgcICQoLDA0ODw:$serviceUuid"

        assertNull(decodePairingPayload(unreadableName))
        assertNull(decodePairingPayload(unreadableSecret))
    }

    @Test
    fun decodeRejectsAnInvalidUuid() {
        val raw = "qrlibre:1:${b64url("Sala".toByteArray())}:" +
            "${b64url(fixedSecret())}:not-a-uuid"

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsAnEmptyDeviceName() {
        val raw = "qrlibre:1::${b64url(fixedSecret())}:$serviceUuid"

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsADeviceNameLongerThanSixtyFourCharacters() {
        val tooLong = "a".repeat(65)
        val raw = "qrlibre:1:${b64url(tooLong.toByteArray())}:" +
            "${b64url(fixedSecret())}:$serviceUuid"

        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeRejectsAPayloadLongerThanFiveHundredTwelveCharacters() {
        val raw = "qrlibre:1:${"A".repeat(600)}:${b64url(fixedSecret())}:$serviceUuid"

        assertTrue(raw.length > 512)
        assertNull(decodePairingPayload(raw))
    }

    @Test
    fun decodeOfEncodeNeverReturnsNullForValidPayloads() {
        val secrets = listOf(fixedSecret(), ByteArray(16) { 0x7f }, randomPairingSecret())

        for (secret in secrets) {
            for (name in listOf("Sala", "Café Central", "a".repeat(64), "Ñandú 🎵")) {
                val raw = payload(deviceName = name, secret = secret).encode()

                assertNotNull("should decode: $raw", decodePairingPayload(raw))
            }
        }
    }

    @Test
    fun equalityComparesSecretContentsNotIdentity() {
        val one = payload(secret = ByteArray(16) { 1 })
        val other = payload(secret = ByteArray(16) { 1 })
        val different = payload(secret = ByteArray(16) { 2 })

        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
        assertFalse(one == different)
    }

    @Test
    fun qrMatrixIsSquareAndLargeEnoughForAValidPayload() {
        val matrix = pairingQrMatrix(payload().encode(), 256)

        assertEquals(matrix.width, matrix.height)
        assertTrue(matrix.width >= 21)
        assertTrue("the matrix must contain dark modules", matrix.toString().contains("X"))
    }
}
