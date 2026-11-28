package co.asssie.qrlibre.pairing

import co.asssie.qrlibre.data.jsonString
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One captured notification as it crosses the Bluetooth link.
 *
 * [verdict] is the owner's classification as the wire token (`payment`,
 * `not_payment`) or null when it has not been set yet. The emitter sends what
 * was stored at capture time; a later classification is not re-sent.
 */
data class CaptureMessage(
    val id: Long,
    val app: String,
    val title: String,
    val body: String,
    val epochMs: Long,
    val verdict: String?,
)

/** Marks the only signature scheme this version produces. */
private const val SIGNATURE_PREFIX = "v1:"

/**
 * Compact JSON with the keys in a fixed order:
 * `id`, `app`, `title`, `body`, `epoch_ms`, `verdict`.
 *
 * Strings use the same escaping as the export document, so the two wire
 * formats cannot drift. The payload contains no raw line break; the newline in
 * [signedFrame] is therefore an unambiguous separator.
 */
fun encodeCaptureMessage(message: CaptureMessage): String =
    "{" +
        "\"id\":${message.id}," +
        "\"app\":${jsonString(message.app)}," +
        "\"title\":${jsonString(message.title)}," +
        "\"body\":${jsonString(message.body)}," +
        "\"epoch_ms\":${message.epochMs}," +
        "\"verdict\":${jsonVerdict(message.verdict)}" +
        "}"

/**
 * `v1:` followed by the unpadded base64url HMAC-SHA256 of [payload]'s UTF-8
 * bytes under [secret].
 */
fun signCaptureMessage(payload: String, secret: ByteArray): String =
    SIGNATURE_PREFIX + base64Url(
        Mac.getInstance(ALGORITHM).run {
            init(SecretKeySpec(secret, ALGORITHM))
            doFinal(payload.toByteArray(Charsets.UTF_8))
        },
    )

/** The wire frame: the JSON payload, one newline, then the signature. */
fun signedFrame(message: CaptureMessage, secret: ByteArray): String {
    val payload = encodeCaptureMessage(message)
    return payload + "\n" + signCaptureMessage(payload, secret)
}

/**
 * Returns the JSON payload when [frame] carries a valid signature for [secret].
 *
 * Returns null for anything malformed: no separator, an empty payload, an
 * unknown signature prefix, invalid base64url, a truncated signature, a wrong
 * secret, or trailing junk. The comparison itself runs in constant time once
 * the lengths agree, and no exception escapes.
 */
fun verifyFrame(frame: String, secret: ByteArray): String? {
    val separator = frame.indexOf('\n')
    if (separator <= 0) return null

    val payload = frame.substring(0, separator)
    val signature = frame.substring(separator + 1)
    if (!signature.startsWith(SIGNATURE_PREFIX)) return null

    val expected = try {
        signCaptureMessage(payload, secret)
    } catch (_: Exception) {
        // An unusable key must fail closed, never throw into the caller.
        return null
    }

    return if (constantTimeEquals(signature, expected)) payload else null
}

private const val ALGORITHM = "HmacSHA256"

/** The verdict field: a quoted wire token or a bare JSON null. */
private fun jsonVerdict(verdict: String?): String =
    if (verdict == null) "null" else jsonString(verdict)

private fun base64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/**
 * Compares two ASCII strings without branching on their content.
 *
 * The length check leaks only the length, which is public for a fixed-size
 * signature. Every byte is visited, so a matching prefix is not observable.
 */
private fun constantTimeEquals(left: String, right: String): Boolean {
    val leftBytes = left.toByteArray(Charsets.UTF_8)
    val rightBytes = right.toByteArray(Charsets.UTF_8)
    if (leftBytes.size != rightBytes.size) return false

    var difference = 0
    for (index in leftBytes.indices) {
        difference = difference or (leftBytes[index].toInt() xor rightBytes[index].toInt())
    }
    return difference == 0
}
