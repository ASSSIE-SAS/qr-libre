package co.asssie.qrlibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestTest {

    @Test
    fun backFromHistoryLetsTheActivityFinish() {
        assertNull(
            "the product screen is the root: back there must not invent a destination",
            backDestination(Dest.HISTORY),
        )
    }

    @Test
    fun backFromEverySubScreenReturnsToHistory() {
        assertEquals(
            "Settings is one level deep",
            Dest.HISTORY,
            backDestination(Dest.SETTINGS),
        )
        assertEquals(
            "Apps is one level deep",
            Dest.HISTORY,
            backDestination(Dest.APPS),
        )
        assertEquals(
            "Privacy is one level deep",
            Dest.HISTORY,
            backDestination(Dest.PRIVACY),
        )
    }

    @Test
    fun gearFromHistoryOpensSettings() {
        assertEquals(
            "the gear in the history header opens Settings",
            Dest.SETTINGS,
            gearDestination(Dest.HISTORY),
        )
    }

    @Test
    fun gearFromSettingsStaysOnSettings() {
        assertEquals(
            "tapping the same affordance twice must not stack destinations",
            Dest.SETTINGS,
            gearDestination(Dest.SETTINGS),
        )
    }

    @Test
    fun gearTargetAlwaysHasABackPath() {
        for (dest in Dest.values()) {
            val target = gearDestination(dest)
            assertEquals(
                "a gear tap must never land on a screen that cannot go back to History",
                Dest.HISTORY,
                backDestination(target),
            )
        }
    }

    @Test
    fun backIsASingleStep() {
        assertEquals(Dest.HISTORY, backDestination(Dest.SETTINGS))
        assertNull(
            "a second back from History exits instead of bouncing between screens",
            backDestination(backDestination(Dest.SETTINGS)!!),
        )
    }
}
