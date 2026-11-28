package co.asssie.qrlibre.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerStateTest {

    @Test
    fun theHappyPathMovesIdleToConnectingToConnected() {
        assertEquals(SpeakerConnection.CONNECTING, SpeakerConnection.IDLE.reduce(SpeakerEvent.Searching))
        assertEquals(
            SpeakerConnection.CONNECTED,
            SpeakerConnection.CONNECTING.reduce(SpeakerEvent.Linked),
        )
    }

    @Test
    fun aDroppedLinkShowsDisconnectedAndCanSearchAgain() {
        assertEquals(
            SpeakerConnection.DISCONNECTED,
            SpeakerConnection.CONNECTED.reduce(SpeakerEvent.Lost),
        )
        assertEquals(
            SpeakerConnection.CONNECTING,
            SpeakerConnection.DISCONNECTED.reduce(SpeakerEvent.Searching),
        )
    }

    @Test
    fun aLateLostCallbackCannotWakeAStoppedReceiver() {
        assertEquals(
            "a stale GATT callback must not undo an explicit stop",
            SpeakerConnection.IDLE,
            SpeakerConnection.IDLE.reduce(SpeakerEvent.Lost),
        )
    }

    @Test
    fun stoppedWinsFromEveryState() {
        for (state in SpeakerConnection.values()) {
            assertEquals(
                "Stopped must always land on IDLE from $state",
                SpeakerConnection.IDLE,
                state.reduce(SpeakerEvent.Stopped),
            )
        }
    }

    @Test
    fun repeatedEventsAreIdempotent() {
        for (state in SpeakerConnection.values()) {
            for (event in listOf(SpeakerEvent.Searching, SpeakerEvent.Linked)) {
                val once = state.reduce(event)
                assertEquals(
                    "$event applied twice must equal once from $state",
                    once,
                    once.reduce(event),
                )
            }
        }
    }
}
