package co.asssie.qrlibre.notifications

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

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

    /**
     * Asks the system to rebind the listener. Needed because a force-stop or a
     * device reboot unbinds it while the notification-access setting stays on.
     * Safe to call when access is not granted, and safe to call repeatedly.
     */
    fun requestRebind(context: Context) {
        if (!isGranted(context)) return
        val component = ComponentName(context, QrLibreNotificationListener::class.java)
        try {
            NotificationListenerService.requestRebind(component)
        } catch (_: RuntimeException) {
            // The system owns the binding; a refusal must not break the screen.
        }
    }

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"
}
