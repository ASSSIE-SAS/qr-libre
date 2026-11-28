package co.asssie.qrlibre.notifications

import co.asssie.qrlibre.notifications.ListenerUiState.ACTIVE
import co.asssie.qrlibre.notifications.ListenerUiState.CONNECTING
import co.asssie.qrlibre.notifications.ListenerUiState.NO_APPS
import co.asssie.qrlibre.notifications.ListenerUiState.PERMISSION_MISSING
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerStatusTest {

    private data class Row(
        val accessGranted: Boolean,
        val listenerConnected: Boolean,
        val selectedApps: Int,
        val expected: ListenerUiState,
    )

    @Test
    fun resolvesTheCompleteTruthTableWithPermissionThenAppsThenConnectionPrecedence() {
        val rows = listOf(
            Row(false, true, 2, PERMISSION_MISSING),
            Row(false, true, 0, PERMISSION_MISSING),
            Row(false, false, 2, PERMISSION_MISSING),
            Row(false, false, 0, PERMISSION_MISSING),
            Row(true, true, 0, NO_APPS),
            Row(true, false, 0, NO_APPS),
            Row(true, false, 2, CONNECTING),
            Row(true, true, 2, ACTIVE),
        )

        rows.forEach { row ->
            assertEquals(
                "access=${row.accessGranted} connected=${row.listenerConnected} " +
                    "apps=${row.selectedApps}",
                row.expected,
                resolveListenerUiState(
                    accessGranted = row.accessGranted,
                    listenerConnected = row.listenerConnected,
                    selectedApps = row.selectedApps,
                ),
            )
        }
    }

    @Test
    fun negativeAppCountIsTreatedAsNoApps() {
        assertEquals(NO_APPS, resolveListenerUiState(true, true, -1))
        assertEquals(NO_APPS, resolveListenerUiState(true, false, -5))
    }

    @Test
    fun missingPermissionStillWinsOverANegativeAppCount() {
        assertEquals(PERMISSION_MISSING, resolveListenerUiState(false, true, -1))
    }
}
