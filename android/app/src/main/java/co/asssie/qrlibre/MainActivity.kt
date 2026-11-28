package co.asssie.qrlibre

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.bluetooth.CaptureReceiver
import co.asssie.qrlibre.notifications.ListenerConnection
import co.asssie.qrlibre.notifications.NotificationAccess
import co.asssie.qrlibre.pairing.CaptureMessage
import co.asssie.qrlibre.pairing.spokenText
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
        // The app is dark only, so the system bars always need light icons even
        // when the phone itself is in light mode. The scrim matches the page so
        // devices that respect it do not show a mismatched band.
        val pageScrim = 0xFF0E0E0E.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(pageScrim),
            navigationBarStyle = SystemBarStyle.dark(pageScrim),
        )
        initTextToSpeech()
        setContent {
            QrLibreTheme {
                val listenerConnected by
                    ListenerConnection.connected.collectAsStateWithLifecycle()
                // The speaker link lives while this activity is visible. A new
                // verified frame is announced once; rememberSaveable keeps a
                // rotation from replaying the same notice.
                val speakerState by
                    CaptureReceiver.get(this@MainActivity).state.collectAsStateWithLifecycle()
                val incoming = speakerState.lastMessage
                var spokenKey by rememberSaveable { mutableStateOf("") }
                LaunchedEffect(incoming?.let { "${it.id}:${it.epochMs}" }) {
                    val message = incoming ?: return@LaunchedEffect
                    // Row ids can be reused after the capture history is
                    // cleared, so the key includes the capture timestamp.
                    val key = "${message.id}:${message.epochMs}"
                    if (key == spokenKey) return@LaunchedEffect
                    spokenKey = key
                    speakReceived(message)
                }
                QrLibreApp(
                    notificationAccess = notificationAccess,
                    listenerConnected = listenerConnected,
                    ttsReady = ttsReady,
                    ttsMessage = ttsMessage,
                    onSpeak = ::speakSample,
                    onOpenNotificationSettings = ::openNotificationSettings,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // In-app only: the receiver starts with the visible app and stops in
        // onStop. No background service keeps the old phone listening.
        try {
            CaptureReceiver.get(this).start()
        } catch (_: Exception) {
            // Bluetooth is optional; capture must still work without it.
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
        // Leaving the app (Home, another app) stops the speaker link and the
        // voice. The speaker only works while QR Libre stays open.
        try {
            CaptureReceiver.get(this).stop()
        } catch (_: Exception) {
            // Shutting down Bluetooth can fail without affecting capture.
        }
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

    /**
     * Speaks one verified notice. When the engine is not ready the caller has
     * already shown the message on the pairing screen, so nothing is lost; the
     * app just stays silent.
     */
    private fun speakReceived(message: CaptureMessage) {
        val engine = tts
        if (engine == null || !ttsReady) return
        val result = try {
            engine.speak(
                spokenText(message),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "$UTTERANCE_ID-${message.id}",
            )
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
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
