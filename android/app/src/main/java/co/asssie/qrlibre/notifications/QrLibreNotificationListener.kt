package co.asssie.qrlibre.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import co.asssie.qrlibre.data.CaptureEvents
import co.asssie.qrlibre.data.CapturesDb
import co.asssie.qrlibre.data.SelectedApps

/**
 * Stores the text of notifications from the apps the user picked.
 * It never posts, dismisses or answers notifications.
 */
class QrLibreNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerConnection.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerConnection.onListenerDisconnected()
    }

    override fun onDestroy() {
        // The process may outlive this service instance. Clear the signal here
        // too, so a killed or unbound listener cannot leave a stale "connected".
        ListenerConnection.onListenerDisconnected()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        val sourcePackage = posted.packageName ?: return
        if (sourcePackage == packageName) return
        if (!SelectedApps(this).isSelected(sourcePackage)) return

        try {
            val notification = posted.notification ?: return
            val extras = notification.extras ?: return
            val title = normalizeNotificationText(
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            )
            val body = normalizeBody(extras)
            if (title.isEmpty() && body.isEmpty()) return

            val rowId = CapturesDb.get(this).insertCapture(
                packageName = sourcePackage,
                app = appLabel(sourcePackage),
                title = title,
                body = body,
                epochMs = posted.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
            if (rowId > 0) CaptureEvents.notifyChanged()
        } catch (error: Exception) {
            // A listener must never crash the notification pipeline.
            Log.w(TAG, "Could not save notification from $sourcePackage", error)
        }
    }

    /** Reads the four text fields the app cares about, without duplicates. */
    private fun normalizeBody(extras: Bundle): String =
        listOf(
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
        )
            .mapNotNull { key -> extras.getCharSequence(key)?.toString() }
            .map(::normalizeNotificationText)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")

    private fun appLabel(packageName: String): String {
        val pm = packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private companion object {
        const val TAG = "QrLibreListener"
    }
}
