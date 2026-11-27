package co.asssie.qrlibre

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.asssie.qrlibre.notifications.NotificationAccess
import co.asssie.qrlibre.ui.QrLibreApp
import co.asssie.qrlibre.ui.QrLibreTheme

class MainActivity : ComponentActivity() {

    private var notificationAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QrLibreTheme {
                QrLibreApp(
                    notificationAccess = notificationAccess,
                    onOpenNotificationSettings = ::openNotificationSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have flipped the switch in system settings.
        notificationAccess = NotificationAccess.isGranted(this)
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // No settings activity to open on this device. The permission
                // card still explains what is missing.
            }
        }
    }
}
