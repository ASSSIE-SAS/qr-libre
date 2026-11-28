package co.asssie.qrlibre.notifications

/** The honest state of notification capture shown on the Historial screen. */
enum class ListenerUiState {
    PERMISSION_MISSING,
    NO_APPS,
    CONNECTING,
    ACTIVE,
}

/**
 * Reduces the three independent signals into one state.
 *
 * Precedence: missing permission, then an empty selection, then the system
 * binding. Only all three signals positive is [ListenerUiState.ACTIVE].
 * A zero or negative app count means the user has selected nothing.
 */
fun resolveListenerUiState(
    accessGranted: Boolean,
    listenerConnected: Boolean,
    selectedApps: Int,
): ListenerUiState = when {
    !accessGranted -> ListenerUiState.PERMISSION_MISSING
    selectedApps <= 0 -> ListenerUiState.NO_APPS
    !listenerConnected -> ListenerUiState.CONNECTING
    else -> ListenerUiState.ACTIVE
}
