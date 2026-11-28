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
        assertFalse("one peer is the limit, so the QR goes away", summary.showsQr)
        assertFalse(summary.showsScanAction)
    }

    @Test
    fun speakerUnpairedOffersTheScanActionAndCanStartIt() {
        val summary = pairingSummary(DeviceRole.SPEAKER, peer = null)

        assertEquals(PairingStatus.SPEAKER_UNPAIRED, summary.status)
        assertEquals(DeviceRole.SPEAKER, summary.role)
        assertNull(summary.peerName)
        assertFalse(summary.paired)
        assertFalse(summary.canUnpair)
        assertFalse("the speaker never shows the QR", summary.showsQr)
        assertTrue("the speaker is the role that scans", summary.showsScanAction)
        assertTrue(
            "the scanner ships with the speaker role, so the button is live",
            summary.scanEnabled,
        )
        assertFalse(canStartScanning(DeviceRole.CAPTURE))
        assertTrue(canStartScanning(DeviceRole.SPEAKER))
    }

    @Test
    fun speakerPairedNamesTheCapturePhoneAndOffersUnpair() {
        val summary = pairingSummary(DeviceRole.SPEAKER, peer)

        assertEquals(PairingStatus.SPEAKER_PAIRED, summary.status)
        assertEquals("Barra", summary.peerName)
        assertTrue(summary.paired)
        assertTrue(summary.canUnpair)
        assertFalse(summary.showsQr)
        assertFalse("one peer is the limit, so the scan button goes away", summary.showsScanAction)
        assertTrue(summary.scanEnabled)
    }

    @Test
    fun onlyAnUnpairedCapturePhoneShowsThePairingQr() {
        assertTrue(
            "the unpaired capture phone is the one that displays the code",
            showsPairingQr(DeviceRole.CAPTURE, peer = null),
        )
        assertFalse(
            "a paired capture phone hides the code until it is unpaired",
            showsPairingQr(DeviceRole.CAPTURE, peer),
        )
        assertFalse(
            "the speaker never displays the code",
            showsPairingQr(DeviceRole.SPEAKER, peer = null),
        )
        assertFalse(
            "the speaker never displays the code, paired or not",
            showsPairingQr(DeviceRole.SPEAKER, peer),
        )
    }

    @Test
    fun onlyAnUnpairedSpeakerOffersTheScanAction() {
        assertTrue(
            "the unpaired speaker is the one that scans",
            canScanForPairing(DeviceRole.SPEAKER, peer = null),
        )
        assertFalse(
            "a paired speaker hides the scan action until it is unpaired",
            canScanForPairing(DeviceRole.SPEAKER, peer),
        )
        assertFalse(
            "the capture phone never scans",
            canScanForPairing(DeviceRole.CAPTURE, peer = null),
        )
        assertFalse(
            "the capture phone never scans, paired or not",
            canScanForPairing(DeviceRole.CAPTURE, peer),
        )
    }

    @Test
    fun theSummaryFollowsThePairingPredicates() {
        for (role in DeviceRole.values()) {
            for (candidate in listOf(null, peer)) {
                val summary = pairingSummary(role, candidate)

                assertEquals(
                    "the QR flag must match the predicate for $role/$candidate",
                    showsPairingQr(role, candidate),
                    summary.showsQr,
                )
                assertEquals(
                    "the scan flag must match the predicate for $role/$candidate",
                    canScanForPairing(role, candidate),
                    summary.showsScanAction,
                )
            }
        }
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
