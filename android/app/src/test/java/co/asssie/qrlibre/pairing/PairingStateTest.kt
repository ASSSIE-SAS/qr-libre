package co.asssie.qrlibre.pairing

import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairedPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingStateTest {

    private val peer = PairedPeer(
        name = "Barra",
        secretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
        serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
    )

    @Test
    fun captureUnpairedShowsTheQrAndNothingToUnpair() {
        val summary = pairingSummary(DeviceRole.CAPTURE, peer = null)

        assertEquals(PairingStatus.CAPTURE_UNPAIRED, summary.status)
        assertEquals(DeviceRole.CAPTURE, summary.role)
        assertNull("an unpaired phone has no peer name to show", summary.peerName)
        assertFalse(summary.paired)
        assertFalse("Desemparejar only exists when a peer is stored", summary.canUnpair)
        assertTrue("the capture phone is the one that shows the QR", summary.showsQr)
        assertFalse("the capture phone never scans", summary.showsScanAction)
    }

    @Test
    fun capturePairedNamesTheSpeakerAndOffersUnpair() {
        val summary = pairingSummary(DeviceRole.CAPTURE, peer)

        assertEquals(PairingStatus.CAPTURE_PAIRED, summary.status)
        assertEquals("Barra", summary.peerName)
        assertTrue(summary.paired)
        assertTrue(summary.canUnpair)
        assertTrue("the QR stays available to pair another speaker", summary.showsQr)
        assertFalse(summary.showsScanAction)
    }

    @Test
    fun speakerUnpairedOffersTheScanActionButCannotStartYet() {
        val summary = pairingSummary(DeviceRole.SPEAKER, peer = null)

        assertEquals(PairingStatus.SPEAKER_UNPAIRED, summary.status)
        assertEquals(DeviceRole.SPEAKER, summary.role)
        assertNull(summary.peerName)
        assertFalse(summary.paired)
        assertFalse(summary.canUnpair)
        assertFalse("the speaker never shows the QR", summary.showsQr)
        assertTrue("the speaker is the role that scans", summary.showsScanAction)
        assertFalse(
            "the scanner is not shipped in this version, so the button stays disabled",
            summary.scanEnabled,
        )
        assertFalse(canStartScanning(DeviceRole.CAPTURE))
        assertFalse(
            "P4 flips this to true for the speaker; this version does not scan",
            canStartScanning(DeviceRole.SPEAKER),
        )
    }

    @Test
    fun speakerPairedNamesTheCapturePhoneAndOffersUnpair() {
        val summary = pairingSummary(DeviceRole.SPEAKER, peer)

        assertEquals(PairingStatus.SPEAKER_PAIRED, summary.status)
        assertEquals("Barra", summary.peerName)
        assertTrue(summary.paired)
        assertTrue(summary.canUnpair)
        assertFalse(summary.showsQr)
        assertTrue(summary.showsScanAction)
        assertFalse(summary.scanEnabled)
    }

    @Test
    fun thePairedFlagAlwaysMatchesTheStatus() {
        for (role in DeviceRole.values()) {
            for (candidate in listOf(null, peer)) {
                val summary = pairingSummary(role, candidate)

                assertEquals(
                    "status and peer must not disagree for $role/$candidate",
                    candidate != null,
                    summary.paired,
                )
                assertEquals(
                    "unpair is offered exactly when a peer exists",
                    candidate != null,
                    summary.canUnpair,
                )
            }
        }
    }

    @Test
    fun theQrAndTheScanActionNeverAppearTogether() {
        for (role in DeviceRole.values()) {
            val summary = pairingSummary(role, peer = null)

            assertFalse(
                "a phone cannot both display and scan the pairing code",
                summary.showsQr && summary.showsScanAction,
            )
        }
    }

    @Test
    fun anExistingPeerIsNeverReplacedByAConnection() {
        assertNull(
            "a second device must not overwrite the speaker already paired",
            peerOnFirstConnection(
                existing = peer,
                remoteName = "Otra barra",
                localSecretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
                serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
            ),
        )
    }

    @Test
    fun theFirstConnectionRecordsTheTrimmedRemoteName() {
        val recorded = peerOnFirstConnection(
            existing = null,
            remoteName = "  Barra  ",
            localSecretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
            serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
        )

        assertEquals("Barra", recorded?.name)
    }

    @Test
    fun aMissingOrBlankRemoteNameFallsBackToTheGenericLabel() {
        for (name in listOf(null, "", " ", "\t\n ")) {
            val recorded = peerOnFirstConnection(
                existing = null,
                remoteName = name,
                localSecretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
                serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
            )

            assertEquals("Dispositivo", recorded?.name)
        }
    }

    @Test
    fun theRecordedPeerKeepsTheLocalSecretAndUuidVerbatim() {
        val recorded = peerOnFirstConnection(
            existing = null,
            remoteName = "Barra",
            localSecretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
            serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
        )

        assertEquals(
            PairedPeer(
                name = "Barra",
                secretBase64Url = "AAECAwQFBgcICQoLDA0ODw",
                serviceUuid = "6f1d3c2a-6d1b-4c54-9d1a-1f0e2c3b4a59",
            ),
            recorded,
        )
    }
}
