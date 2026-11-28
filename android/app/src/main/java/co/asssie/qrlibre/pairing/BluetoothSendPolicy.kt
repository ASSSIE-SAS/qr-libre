package co.asssie.qrlibre.pairing

import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairedPeer

/**
 * Whether this phone should announce the capture service over Bluetooth.
 *
 * Only the capture phone advertises; the speaker is the client that scans and
 * connects. Advertising does not depend on the stored peer: the receiver must
 * be able to discover the service from the QR before a peer exists. The peer
 * parameter is kept so both gates read the same persisted state.
 */
fun shouldAdvertise(role: DeviceRole, peer: PairedPeer?): Boolean =
    role == DeviceRole.CAPTURE

/**
 * Whether a captured message may be sent to the peer right now.
 *
 * All three conditions must hold: this phone captures, a peer is stored and
 * the GATT link is up. Anything else fails closed, so an unpaired,
 * disconnected or speaker phone never transmits.
 */
fun shouldSend(role: DeviceRole, peer: PairedPeer?, connected: Boolean): Boolean =
    role == DeviceRole.CAPTURE && peer != null && connected
