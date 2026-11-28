package co.asssie.qrlibre.ui

import co.asssie.qrlibre.notifications.ListenerUiState.ACTIVE
import co.asssie.qrlibre.notifications.ListenerUiState.CONNECTING
import co.asssie.qrlibre.notifications.ListenerUiState.NO_APPS
import co.asssie.qrlibre.notifications.ListenerUiState.PERMISSION_MISSING
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPresentationTest {

    private val inactiveStates = listOf(PERMISSION_MISSING, NO_APPS, CONNECTING)

    @Test
    fun savedCapturesStayVisibleInEveryListenerState() {
        (inactiveStates + ACTIVE).forEach { status ->
            val presentation = resolveHistoryPresentation(status, hasCaptures = true)

            assertEquals("saved captures must stay visible for $status", true, presentation.showCaptures)
            assertEquals("no empty billboard while records exist for $status", false, presentation.showEmptyHistory)
        }
    }

    @Test
    fun emptyHistoryIsOnlyOfferedInTheActiveState() {
        inactiveStates.forEach { status ->
            val presentation = resolveHistoryPresentation(status, hasCaptures = false)

            assertEquals("no empty billboard before setup for $status", false, presentation.showEmptyHistory)
            assertEquals("nothing to render as captures for $status", false, presentation.showCaptures)
        }

        val active = resolveHistoryPresentation(ACTIVE, hasCaptures = false)
        assertEquals("an active empty history teaches the next step", true, active.showEmptyHistory)
        assertEquals(false, active.showCaptures)
    }

    @Test
    fun voiceTestOnlyAppearsWhenCaptureIsActive() {
        inactiveStates.forEach { status ->
            assertEquals(
                "no voice test before capture is active for $status",
                false,
                resolveHistoryPresentation(status, hasCaptures = false).showVoiceTest,
            )
        }

        assertEquals(true, resolveHistoryPresentation(ACTIVE, hasCaptures = false).showVoiceTest)
        assertEquals(true, resolveHistoryPresentation(ACTIVE, hasCaptures = true).showVoiceTest)
    }
}
