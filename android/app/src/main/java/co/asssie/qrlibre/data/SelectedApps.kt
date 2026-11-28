package co.asssie.qrlibre.data

import android.content.Context

/**
 * Packages the user chose to capture, stored as a string set in preferences.
 */
class SelectedApps(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun selected(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun isSelected(packageName: String): Boolean = selected().contains(packageName)

    /**
     * Adds or removes one package and returns the resulting selection.
     * Every writer goes through here, so the change signal is emitted in one
     * place. A request that would not change the set writes nothing and emits
     * nothing, avoiding duplicate reloads.
     */
    fun setSelected(packageName: String, selected: Boolean): Set<String> {
        val current = selected()
        if (current.contains(packageName) == selected) return current

        val updated = current.toMutableSet()
        if (selected) {
            updated.add(packageName)
        } else {
            updated.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_PACKAGES, updated).apply()
        SelectionEvents.notifyChanged()
        return updated
    }

    private companion object {
        const val PREFS_NAME = "qr_libre_settings"
        const val KEY_PACKAGES = "selected_packages"
    }
}
