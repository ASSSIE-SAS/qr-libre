package co.asssie.qrlibre.data

import android.content.Context
import co.asssie.qrlibre.pairing.PairingPayload
import co.asssie.qrlibre.pairing.randomPairingSecret
import java.util.Base64
import java.util.UUID

/** What this phone does in a capture/speaker pair. */
enum class DeviceRole {
    CAPTURE,
    SPEAKER,
}

/**
 * The other phone in the pair, as carried by the pairing QR.
 *
 * [secretBase64Url] is the HMAC key, base64url without padding. It is kept
 * encoded everywhere outside the crypto code and must never be logged.
 */
data class PairedPeer(
    val name: String,
    val secretBase64Url: String,
    val serviceUuid: String,
)

/**
 * Persisted pairing state: the role this phone plays, the single stored peer,
 * and the emitter identity shown in the QR.
 *
 * Preferences are private to the app and the secret is written as base64url,
 * never as text that could leak into a log or a backup (backups are off).
 */
class PairingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Defaults to [DeviceRole.CAPTURE] so a fresh install keeps capturing as today. */
    fun role(): DeviceRole =
        when (prefs.getString(KEY_ROLE, null)) {
            DeviceRole.SPEAKER.name -> DeviceRole.SPEAKER
            // Anything unreadable falls back to the role the app already ships.
            else -> DeviceRole.CAPTURE
        }

    /**
     * Persists [role]. A real role change also clears the peer: a pairing is
     * bound to the direction it was made for, so a stored speaker must not
     * become the peer of a phone that now sounds.
     */
    fun setRole(role: DeviceRole) {
        val changed = role() != role
        val editor = prefs.edit().putString(KEY_ROLE, role.name)
        if (changed) removePeer(editor)
        editor.apply()
    }

    /** The stored peer, or null when unpaired or when the stored data is unusable. */
    fun peer(): PairedPeer? {
        val name = prefs.getString(KEY_PEER_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val secret = prefs.getString(KEY_PEER_SECRET, null)
            ?.takeIf { decodesToSecret(it) }
            ?: return null
        val uuid = prefs.getString(KEY_PEER_UUID, null)
            ?.takeIf(::isUuid)
            ?: return null
        return PairedPeer(name = name, secretBase64Url = secret, serviceUuid = uuid)
    }

    /** Stores [peer], replacing any previous one. There is only ever one. */
    fun savePeer(peer: PairedPeer) {
        prefs.edit()
            .putString(KEY_PEER_NAME, peer.name)
            .putString(KEY_PEER_SECRET, peer.secretBase64Url)
            .putString(KEY_PEER_UUID, peer.serviceUuid)
            .apply()
    }

    /** Forgets the peer without touching the role. */
    fun clearPeer() {
        removePeer(prefs.edit()).apply()
    }

    /**
     * The emitter identity this phone shows in its QR, or null before it has
     * ever been created. The secret is only minted for the capture role.
     */
    fun localPayload(): PairingPayload? {
        val name = prefs.getString(KEY_LOCAL_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val secret = prefs.getString(KEY_LOCAL_SECRET, null)
            ?.takeIf { decodesToSecret(it) }
            ?.let(::decodeSecret)
            ?: return null
        val uuid = prefs.getString(KEY_LOCAL_UUID, null)
            ?.takeIf(::isUuid)
            ?: return null
        return PairingPayload(deviceName = name, secret = secret, serviceUuid = uuid)
    }

    /**
     * Returns the persisted emitter identity, creating it once on first use.
     *
     * The secret and the service UUID are minted here exactly once; later calls
     * and app restarts return the same bytes, so the QR does not change while
     * the role stays [DeviceRole.CAPTURE].
     */
    fun localPayloadOrCreate(deviceName: String): PairingPayload {
        localPayload()?.let { return it }

        val payload = PairingPayload(
            deviceName = deviceName.ifBlank { FALLBACK_DEVICE_NAME }.take(MAX_DEVICE_NAME),
            secret = randomPairingSecret(),
            serviceUuid = UUID.randomUUID().toString(),
        )
        prefs.edit()
            .putString(KEY_LOCAL_NAME, payload.deviceName)
            .putString(KEY_LOCAL_SECRET, encodeSecret(payload.secret))
            .putString(KEY_LOCAL_UUID, payload.serviceUuid)
            .apply()
        return payload
    }

    private fun removePeer(
        editor: android.content.SharedPreferences.Editor,
    ): android.content.SharedPreferences.Editor =
        editor
            .remove(KEY_PEER_NAME)
            .remove(KEY_PEER_SECRET)
            .remove(KEY_PEER_UUID)

    private companion object {
        const val PREFS_NAME = "qr_libre_pairing"
        const val KEY_ROLE = "role"
        const val KEY_PEER_NAME = "peer_name"
        const val KEY_PEER_SECRET = "peer_secret"
        const val KEY_PEER_UUID = "peer_uuid"
        const val KEY_LOCAL_NAME = "local_name"
        const val KEY_LOCAL_SECRET = "local_secret"
        const val KEY_LOCAL_UUID = "local_uuid"

        const val SECRET_BYTES = 16
        const val MAX_DEVICE_NAME = 64
        const val FALLBACK_DEVICE_NAME = "QR Libre"
    }
}

private fun encodeSecret(secret: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(secret)

private fun decodeSecret(text: String): ByteArray? =
    try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun decodesToSecret(text: String): Boolean = decodeSecret(text)?.size == 16

private fun isUuid(text: String): Boolean =
    try {
        UUID.fromString(text)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
