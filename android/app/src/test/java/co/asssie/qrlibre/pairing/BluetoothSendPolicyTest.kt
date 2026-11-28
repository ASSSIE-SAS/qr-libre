package co.asssie.qrlibre.pairing

import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairedPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSendPolicyTest {

    private val peer = PairedPeer(
        name = "Barra",
        secretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
        serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
    )

    @Test
    fun theCapturePhoneAdvertisesWithAndWithoutAPeer() {
        assertTrue(
            "a fresh emitter must be discoverable before a peer exists",
            shouldAdvertise(DeviceRole.CAPTURE, peer = null),
        )
        assertTrue(shouldAdvertise(DeviceRole.CAPTURE, peer))
    }

    @Test
    fun theSpeakerNeverAdvertises() {
        assertFalse(shouldAdvertise(DeviceRole.SPEAKER, peer = null))
        assertFalse(shouldAdvertise(DeviceRole.SPEAKER, peer))
    }

    @Test
    fun sendRequiresCapturePairedAndConnected() {
        assertTrue(shouldSend(DeviceRole.CAPTURE, peer, connected = true))

        assertFalse(
            "an unpaired emitter has nowhere to send",
            shouldSend(DeviceRole.CAPTURE, peer = null, connected = true),
        )
        assertFalse(
            "a dropped link stops sending",
            shouldSend(DeviceRole.CAPTURE, peer, connected = false),
        )
        assertFalse(
            "the speaker never transmits capture frames",
            shouldSend(DeviceRole.SPEAKER, peer, connected = true),
        )
    }

    @Test
    fun everyCombinationFailsClosedExceptTheOneAllowedSend() {
        for (role in DeviceRole.values()) {
            for (candidate in listOf(null, peer)) {
                for (connected in listOf(false, true)) {
                    val expected = role == DeviceRole.CAPTURE &&
                        candidate != null &&
                        connected
                    assertEquals(
                        "unexpected send decision for $role/$candidate/$connected",
                        expected,
                        shouldSend(role, candidate, connected),
                    )
                }
            }
        }
    }
}
