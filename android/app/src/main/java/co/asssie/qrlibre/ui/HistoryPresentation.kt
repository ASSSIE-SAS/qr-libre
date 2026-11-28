package co.asssie.qrlibre.ui

import co.asssie.qrlibre.notifications.ListenerUiState

/**
 * Visibility decisions the Historial screen consumes for a loaded capture
 * list. Kept pure so the first-run gating is covered without Compose UI tests.
 */
data class HistoryPresentation(
    val showCaptures: Boolean,
    val showEmptyHistory: Boolean,
    val showVoiceTest: Boolean,
)

fun resolveHistoryPresentation(
    status: ListenerUiState,
    hasCaptures: Boolean,
): HistoryPresentation = HistoryPresentation(
    showCaptures = hasCaptures,
    showEmptyHistory = status == ListenerUiState.ACTIVE && !hasCaptures,
    showVoiceTest = status == ListenerUiState.ACTIVE,
)
