package co.asssie.qrlibre.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5132),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0E2),
    onPrimaryContainer = Color(0xFF06291A),
    secondary = Color(0xFF4A6355),
    secondaryContainer = Color(0xFFE3EFE7),
    onSecondaryContainer = Color(0xFF1F3328),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFE8EFEA),
    onSurfaceVariant = Color(0xFF424940),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD7B8),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF0F5132),
    onPrimaryContainer = Color(0xFFD7F0E2),
    secondary = Color(0xFFB1CCBC),
    secondaryContainer = Color(0xFF314C3E),
    onSecondaryContainer = Color(0xFFCDE9D7),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C0),
)

@Composable
fun QrLibreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
