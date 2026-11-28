package co.asssie.qrlibre.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Signals that the selected apps changed so open screens can reload the count.
 * Emitted centrally by [SelectedApps.setSelected].
 */
object SelectionEvents {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { it + 1 }
    }
}
