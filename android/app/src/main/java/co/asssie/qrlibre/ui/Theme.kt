package co.asssie.qrlibre.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.asssie.qrlibre.R

/**
 * Tokens taken from the reference site's live stylesheet. Only the palette is
 * borrowed; no brand asset or copy comes from there.
 *
 * The ARGB constants are pure [Long] values so the JUnit contrast test can pin
 * the real palette without a Compose or rendering dependency. Production
 * constructs its [Color] values from this single source.
 */
internal object QrLibreColorValues {
    const val Page = 0xFF0E0E0EL
    const val Card = 0xFF171717L
    const val Elevated = 0xFF1E1E1EL
    const val Divider = 0xFF262626L
    const val Lime = 0xFFCFFF2EL
    const val OnLime = 0xFF0B0B0BL
    const val Green = 0xFF00DF1AL
    const val Warning = 0xFFF6B60BL
    const val Danger = 0xFFEB3131L
    const val DangerText = 0xFFFF7A7AL
    const val Text = 0xFFF5F4F0L
    const val Muted = 0xFFA2A2A1L
    const val SwitchOff = 0xFF3A3A3AL

    /**
     * Lime at 80% alpha for long technical text. Composited over #171717 this
     * lands near #A9D32C, roughly 9:1 contrast, so readability survives.
     */
    const val LimeText = 0xCCCFFF2EL
}

internal object QrLibreColors {
    val Page = Color(QrLibreColorValues.Page)
    val Card = Color(QrLibreColorValues.Card)
    val Elevated = Color(QrLibreColorValues.Elevated)
    val Divider = Color(QrLibreColorValues.Divider)
    val Lime = Color(QrLibreColorValues.Lime)
    val OnLime = Color(QrLibreColorValues.OnLime)
    val Green = Color(QrLibreColorValues.Green)
    val Warning = Color(QrLibreColorValues.Warning)
    val Danger = Color(QrLibreColorValues.Danger)
    val DangerText = Color(QrLibreColorValues.DangerText)
    val Text = Color(QrLibreColorValues.Text)
    val Muted = Color(QrLibreColorValues.Muted)
    val SwitchOff = Color(QrLibreColorValues.SwitchOff)
    val LimeText = Color(QrLibreColorValues.LimeText)
}

internal val GeistMonoFamily = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
)

private val GeistFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_bold, FontWeight.Bold),
)

internal val QrLibreDarkColors = darkColorScheme(
    primary = QrLibreColors.Lime,
    onPrimary = QrLibreColors.OnLime,
    primaryContainer = QrLibreColors.Lime,
    onPrimaryContainer = QrLibreColors.OnLime,
    inversePrimary = QrLibreColors.OnLime,
    secondary = QrLibreColors.Green,
    onSecondary = QrLibreColors.OnLime,
    secondaryContainer = QrLibreColors.Elevated,
    onSecondaryContainer = QrLibreColors.Text,
    tertiary = QrLibreColors.Warning,
    onTertiary = QrLibreColors.OnLime,
    tertiaryContainer = QrLibreColors.Elevated,
    onTertiaryContainer = QrLibreColors.Warning,
    background = QrLibreColors.Page,
    onBackground = QrLibreColors.Text,
    surface = QrLibreColors.Card,
    onSurface = QrLibreColors.Text,
    surfaceVariant = QrLibreColors.Elevated,
    onSurfaceVariant = QrLibreColors.Muted,
    surfaceTint = QrLibreColors.Lime,
    surfaceBright = QrLibreColors.Elevated,
    surfaceDim = QrLibreColors.Page,
    surfaceContainerLowest = QrLibreColors.Page,
    surfaceContainerLow = QrLibreColors.Card,
    surfaceContainer = QrLibreColors.Card,
    surfaceContainerHigh = QrLibreColors.Elevated,
    surfaceContainerHighest = QrLibreColors.Divider,
    inverseSurface = QrLibreColors.Text,
    inverseOnSurface = QrLibreColors.Page,
    error = QrLibreColors.Danger,
    onError = QrLibreColors.OnLime,
    errorContainer = QrLibreColors.Elevated,
    onErrorContainer = QrLibreColors.DangerText,
    outline = QrLibreColors.SwitchOff,
    outlineVariant = QrLibreColors.Divider,
    scrim = Color.Black,
)

private val QrLibreTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    ),
)

private val QrLibreShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(percent = 50),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
)

/** Technical text: capture bodies, package names and other machine output. */
internal val MonoBody = TextStyle(
    fontFamily = GeistMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

@Composable
fun QrLibreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QrLibreDarkColors,
        typography = QrLibreTypography,
        shapes = QrLibreShapes,
        content = content,
    )
}
