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
    /** The speaker screen offers the scan action; the capture phone never does. */
    val showsScanAction: Boolean,
    /** Whether the scan action can run in this build. */
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
        showsQr = showsPairingQr(role, peer),
        showsScanAction = canScanForPairing(role, peer),
        scanEnabled = canStartScanning(role),
    )
}

/**
 * Only an unpaired capture phone has a code to show. There is room for a single
 * peer, so once one is stored the QR disappears until the user unpairs; showing
 * it again would invite a second phone that can never be recorded.
 */
fun showsPairingQr(role: DeviceRole, peer: PairedPeer?): Boolean =
    role == DeviceRole.CAPTURE && peer == null

/**
 * Only an unpaired speaker has a code to scan. With the single-peer limit, a
 * paired speaker can no longer store another phone, so the action is hidden
 * rather than offered and then ignored.
 */
fun canScanForPairing(role: DeviceRole, peer: PairedPeer?): Boolean =
    role == DeviceRole.SPEAKER && peer == null

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
 * The scanner ships with the speaker role and the camera permission. Only the
 * speaker scans; the capture phone is the one that displays the code.
 */
private const val SCANNER_SHIPPED = true

fun canStartScanning(role: DeviceRole): Boolean =
    SCANNER_SHIPPED && role == DeviceRole.SPEAKER
