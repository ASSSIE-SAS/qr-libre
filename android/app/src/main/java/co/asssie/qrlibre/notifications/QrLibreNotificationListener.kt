package co.asssie.qrlibre.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import co.asssie.qrlibre.bluetooth.CaptureBroadcaster
import co.asssie.qrlibre.data.CaptureEvents
import co.asssie.qrlibre.data.CapturesDb
import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairingStore
import co.asssie.qrlibre.data.SelectedApps
import co.asssie.qrlibre.pairing.CaptureMessage

/**
 * Stores the text of notifications from the apps the user picked.
 * It never posts, dismisses or answers notifications.
 */
class QrLibreNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerConnection.onListenerConnected()
        // A capture phone may already have a role, a QR payload and a peer.
        // This reconciles advertising after a reboot or a rebind.
        try {
            CaptureBroadcaster.get(this).start()
        } catch (error: Exception) {
            Log.d(TAG, "Bluetooth start skipped")
        }
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

            val app = appLabel(sourcePackage)
            val epochMs = posted.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
            val rowId = CapturesDb.get(this).insertCapture(
                packageName = sourcePackage,
                app = app,
                title = title,
                body = body,
                epochMs = epochMs,
            )
            if (rowId > 0) {
                CaptureEvents.notifyChanged()
                broadcastCapture(
                    CaptureMessage(
                        id = rowId,
                        app = app,
                        title = title,
                        body = body,
                        epochMs = epochMs,
                        verdict = null,
                    ),
                )
            }
        } catch (error: Exception) {
            // A listener must never crash the notification pipeline.
            Log.w(TAG, "Could not save notification from $sourcePackage", error)
        }
    }

    /**
     * Forwards an already stored capture when this phone is the emitter.
     *
     * Guarded separately from the insert: a Bluetooth failure is logged without
     * text and can never roll back or hide the capture that is already stored.
     */
    private fun broadcastCapture(message: CaptureMessage) {
        try {
            if (PairingStore(this).role() != DeviceRole.CAPTURE) return
            CaptureBroadcaster.get(this).onCaptured(message)
        } catch (error: Exception) {
            Log.d(TAG, "Bluetooth send skipped: ${error.javaClass.simpleName}")
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
