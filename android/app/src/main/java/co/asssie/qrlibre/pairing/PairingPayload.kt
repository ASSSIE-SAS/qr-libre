package co.asssie.qrlibre.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private const val PREFIX = "qrlibre"
private const val CURRENT_VERSION = 1
private const val SECRET_BYTES = 16
private const val MAX_DEVICE_NAME = 64
private const val MAX_PAYLOAD_LENGTH = 512

/**
 * What the emitter shows in a QR and the receiver keeps after scanning.
 *
 * [secret] is the HMAC-SHA256 key shared by both phones; it is never announced
 * over Bluetooth, only carried inside the QR. [serviceUuid] is the GATT service
 * the receiver looks for. [version] is the wire version and stays 1 for now.
 */
data class PairingPayload(
    val deviceName: String,
    val secret: ByteArray,
    val serviceUuid: String,
    val version: Int = CURRENT_VERSION,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingPayload) return false
        return deviceName == other.deviceName &&
            secret.contentEquals(other.secret) &&
            serviceUuid == other.serviceUuid &&
            version == other.version
    }

    override fun hashCode(): Int {
        var result = deviceName.hashCode()
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + serviceUuid.hashCode()
        result = 31 * result + version
        return result
    }
}

/**
 * The QR text: `qrlibre:1:<deviceName b64url>:<secret b64url>:<serviceUuid>`.
 *
 * Fields are base64url without padding, so the payload never contains spaces or
 * a second colon. The UUID is written verbatim.
 */
fun PairingPayload.encode(): String =
    "$PREFIX:$version:${encodeBase64Url(deviceName.toByteArray(Charsets.UTF_8))}:" +
        "${encodeBase64Url(secret)}:$serviceUuid"

/**
 * Parses a scanned payload, or null for anything invalid.
 *
 * Rejects an unknown prefix or version, a device name that is empty or longer
 * than [MAX_DEVICE_NAME] characters once decoded, a secret that is not exactly
 * [SECRET_BYTES] bytes, malformed base64url, a UUID that `UUID.fromString`
 * refuses, and any raw string longer than [MAX_PAYLOAD_LENGTH] characters. It
 * never throws.
 */
fun decodePairingPayload(raw: String): PairingPayload? {
    if (raw.length > MAX_PAYLOAD_LENGTH) return null

    val parts = raw.split(':')
    if (parts.size != 5) return null
    if (parts[0] != PREFIX) return null

    val version = parts[1].toIntOrNull() ?: return null
    if (version != CURRENT_VERSION) return null

    val nameBytes = decodeBase64Url(parts[2]) ?: return null
    val deviceName = String(nameBytes, Charsets.UTF_8)
    if (deviceName.isEmpty() || deviceName.length > MAX_DEVICE_NAME) return null

    val secret = decodeBase64Url(parts[3]) ?: return null
    if (secret.size != SECRET_BYTES) return null

    val serviceUuid = parts[4]
    try {
        UUID.fromString(serviceUuid)
    } catch (_: IllegalArgumentException) {
        return null
    }

    return PairingPayload(
        deviceName = deviceName,
        secret = secret,
        serviceUuid = serviceUuid,
        version = version,
    )
}

/**
 * A square QR matrix of [sizePx] pixels for [payload].
 *
 * Pure JVM: the caller turns the matrix into a Bitmap. A margin of one module
 * keeps the code tight; error correction M tolerates a slightly dirty screen.
 */
fun pairingQrMatrix(payload: String, sizePx: Int): BitMatrix =
    QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        ),
    )

/** A fresh 16-byte HMAC key from [SecureRandom]. */
fun randomPairingSecret(): ByteArray =
    ByteArray(SECRET_BYTES).also { RANDOM.nextBytes(it) }

private val RANDOM = SecureRandom()

/**
 * The secret as base64url without padding, the form the QR and the store use.
 *
 * Kept shared so the emitter can record its own key on the first connection
 * without minting a second encoder; never log the result.
 */
internal fun encodeBase64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun decodeBase64Url(text: String): ByteArray? =
    try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
