package co.asssie.qrlibre.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Signals that the capture table changed so open screens can reload it.
 * The listener service and the activity usually share one process.
 */
object CaptureEvents {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { it + 1 }
    }
}
