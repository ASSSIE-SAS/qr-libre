package co.asssie.qrlibre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.asssie.qrlibre.ui.QrLibreApp
import co.asssie.qrlibre.ui.QrLibreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QrLibreTheme {
                QrLibreApp()
            }
        }
    }
}
