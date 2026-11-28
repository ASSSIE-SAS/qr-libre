package co.asssie.qrlibre.pairing

import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairedPeer

/** The four states the pairing screen distinguishes. */
enum class PairingStatus {
    CAPTURE_UNPAIRED,
    CAPTURE_PAIRED,
    SPEAKER_UNPAIRED,
    SPEAKER_PAIRED,
}

/**
 * What the pairing screen renders for the persisted role and peer.
 *
 * [status] is the label key; the screen maps it to the Spanish copy in
 * `strings.xml`, so this model stays pure and every user-facing string stays
 * in resources.
 */
data class PairingSummary(
    val status: PairingStatus,
    val role: DeviceRole,
    val peerName: String?,
    /** The capture phone shows the emitter QR; the speaker never does. */
    val showsQr: Boolean,
    /** The speaker screen offers the scan action (disabled until P4). */
    val showsScanAction: Boolean,
    /** Whether the scan action can run in this build. False until P4. */
    val scanEnabled: Boolean,
) {
    val paired: Boolean get() = peerName != null

    /** "Desemparejar" only exists when there is a peer to forget. */
    val canUnpair: Boolean get() = peerName != null
}

fun pairingSummary(role: DeviceRole, peer: PairedPeer?): PairingSummary {
    val paired = peer != null
    return PairingSummary(
        status = when (role) {
            DeviceRole.CAPTURE ->
                if (paired) PairingStatus.CAPTURE_PAIRED else PairingStatus.CAPTURE_UNPAIRED

            DeviceRole.SPEAKER ->
                if (paired) PairingStatus.SPEAKER_PAIRED else PairingStatus.SPEAKER_UNPAIRED
        },
        role = role,
        peerName = peer?.name,
        showsQr = role == DeviceRole.CAPTURE,
        showsScanAction = role == DeviceRole.SPEAKER,
        scanEnabled = canStartScanning(role),
    )
}

/**
 * The peer this emitter records when the first speaker connects, or null when
 * a peer is already stored.
 *
 * The QR flow only teaches the receiver the secret: whoever scans the code can
 * sign its requests with it, so the first device that reaches the capture
 * service is authorized by that fact alone. Recording it lets the pairing
 * screen name the speaker and offer "Desemparejar"; it grants no extra trust
 * beyond the secret the scanner already holds.
 *
 * A stored peer is never replaced: [existing] wins and the result is null, so
 * a later connection can only be ignored, never silently promoted. [remoteName]
 * is trimmed and must be non-empty, otherwise [FALLBACK_PEER_NAME] is used.
 * The secret and the service UUID are the emitter's own and pass through
 * verbatim, encoded exactly as the QR writes them.
 */
fun peerOnFirstConnection(
    existing: PairedPeer?,
    remoteName: String?,
    localSecretBase64Url: String,
    serviceUuid: String,
): PairedPeer? {
    if (existing != null) return null
    return PairedPeer(
        name = remoteName?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_PEER_NAME,
        secretBase64Url = localSecretBase64Url,
        serviceUuid = serviceUuid,
    )
}

/** Shown when a connection carries no usable device name. */
private const val FALLBACK_PEER_NAME = "Dispositivo"

/**
 * The scanner ships in P4 together with the camera permission. Only the
 * speaker role scans, so this version answers false for both roles and the
 * button stays visible but disabled. P4 returns `role == DeviceRole.SPEAKER`.
 */
private const val SCANNER_SHIPPED = false

fun canStartScanning(role: DeviceRole): Boolean =
    SCANNER_SHIPPED && role == DeviceRole.SPEAKER
