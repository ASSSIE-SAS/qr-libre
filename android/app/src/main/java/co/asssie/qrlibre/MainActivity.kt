package co.asssie.qrlibre

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.asssie.qrlibre.notifications.NotificationAccess
import co.asssie.qrlibre.ui.QrLibreApp
import co.asssie.qrlibre.ui.QrLibreTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady by mutableStateOf(false)
    private var ttsMessage by mutableStateOf<String?>(null)
    private var notificationAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initTextToSpeech()
        setContent {
            QrLibreTheme {
                QrLibreApp(
                    notificationAccess = notificationAccess,
                    ttsReady = ttsReady,
                    ttsMessage = ttsMessage,
                    onSpeak = ::speakSample,
                    onOpenNotificationSettings = ::openNotificationSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have flipped the switch in system settings.
        notificationAccess = NotificationAccess.isGranted(this)
        // A force-stop or a reboot leaves the listener unbound even while the
        // setting stays on. Asking the system to rebind fixes that on open.
        NotificationAccess.requestRebind(this)
    }

    override fun onStop() {
        // Leaving the app (Home, another app) should not keep speaking.
        tts?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    val colombian = engine?.setLanguage(Locale.forLanguageTag("es-CO"))
                        ?: TextToSpeech.ERROR
                    val language = if (colombian < TextToSpeech.LANG_AVAILABLE) {
                        engine?.setLanguage(Locale.forLanguageTag("es"))
                            ?: TextToSpeech.ERROR
                    } else {
                        colombian
                    }
                    if (language < TextToSpeech.LANG_AVAILABLE) {
                        ttsReady = false
                        ttsMessage = getString(R.string.voice_language_missing)
                    } else {
                        ttsReady = true
                        ttsMessage = null
                    }
                } else {
                    ttsReady = false
                    ttsMessage = getString(R.string.voice_unavailable)
                }
            }
        } catch (_: Exception) {
            tts = null
            ttsReady = false
            ttsMessage = getString(R.string.voice_unavailable)
        }
    }

    private fun speakSample() {
        ttsMessage = null
        val engine = tts
        if (engine == null || !ttsReady) {
            ttsMessage = getString(R.string.voice_unavailable)
            return
        }
        val result = engine.speak(
            getString(R.string.voice_sentence),
            TextToSpeech.QUEUE_FLUSH,
            null,
            UTTERANCE_ID,
        )
        if (result == TextToSpeech.ERROR) {
            ttsMessage = getString(R.string.voice_error)
        }
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

    private companion object {
        const val UTTERANCE_ID = "qr-libre-sample"
    }
}
