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

    /** Adds or removes one package and returns the resulting selection. */
    fun setSelected(packageName: String, selected: Boolean): Set<String> {
        val updated = selected().toMutableSet()
        if (selected) {
            updated.add(packageName)
        } else {
            updated.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_PACKAGES, updated).apply()
        return updated
    }

    private companion object {
        const val PREFS_NAME = "qr_libre_settings"
        const val KEY_PACKAGES = "selected_packages"
    }
}
