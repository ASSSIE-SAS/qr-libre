package co.asssie.qrlibre.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local signal for the system binding of the notification listener.
 *
 * It is not persisted: after a process death the default is disconnected until
 * the system delivers the listener again. The service owns the lifecycle and
 * reports both a disconnect and its own destruction, so a stale `true` cannot
 * survive the service going away.
 */
object ListenerConnection {

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun onListenerConnected() {
        _connected.value = true
    }

    fun onListenerDisconnected() {
        _connected.value = false
    }
}
