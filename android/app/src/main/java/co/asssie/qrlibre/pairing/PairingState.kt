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
 * The scanner ships in P4 together with the camera permission. Only the
 * speaker role scans, so this version answers false for both roles and the
 * button stays visible but disabled. P4 returns `role == DeviceRole.SPEAKER`.
 */
private const val SCANNER_SHIPPED = false

fun canStartScanning(role: DeviceRole): Boolean =
    SCANNER_SHIPPED && role == DeviceRole.SPEAKER
