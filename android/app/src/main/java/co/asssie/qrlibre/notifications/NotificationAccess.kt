package co.asssie.qrlibre.notifications

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationAccess {

    /**
     * Reads the same system setting the notification-access screen writes.
     * Comparing the package name accepts listener aliases and class renames.
     */
    fun isGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_LISTENERS,
        ) ?: return false
        if (enabled.isBlank()) return false
        return enabled.split(':').any { flat ->
            ComponentName.unflattenFromString(flat)?.packageName == context.packageName
        } || enabled.contains("${context.packageName}/")
    }

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"
}
