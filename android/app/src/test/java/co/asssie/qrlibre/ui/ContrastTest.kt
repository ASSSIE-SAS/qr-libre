package co.asssie.qrlibre.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the accessibility contract of the real dark palette. The ratio math is
 * intentionally test-local: production ships constants, not a contrast engine.
 */
class ContrastTest {

    private val surfaces = listOf(
        "Page" to QrLibreColorValues.Page,
        "Card" to QrLibreColorValues.Card,
        "Elevated" to QrLibreColorValues.Elevated,
        "Divider" to QrLibreColorValues.Divider,
    )

    @Test
    fun errorTextOnFilledErrorSurfaceMeetsNormalTextContrast() {
        assertContrast(
            label = "onError on error",
            foreground = QrLibreDarkColors.onError.argbLong(),
            background = QrLibreDarkColors.error.argbLong(),
        )
    }

    @Test
    fun errorTextOnErrorContainerMeetsNormalTextContrast() {
        assertContrast(
            label = "onErrorContainer on errorContainer",
            foreground = QrLibreDarkColors.onErrorContainer.argbLong(),
            background = QrLibreDarkColors.errorContainer.argbLong(),
        )
    }

    @Test
    fun visibleDangerTextMeetsNormalTextContrastOnEverySurface() {
        surfaces.forEach { (name, surface) ->
            assertContrast(
                label = "DangerText on $name",
                foreground = QrLibreColorValues.DangerText,
                background = surface,
            )
        }
    }

    @Test
    fun baselineBodyAndMutedTextStayReadableOnEverySurface() {
        val foregrounds = listOf(
            "Text" to QrLibreColorValues.Text,
            "Muted" to QrLibreColorValues.Muted,
        )
        foregrounds.forEach { (foregroundName, foreground) ->
            surfaces.forEach { (surfaceName, surface) ->
                assertContrast(
                    label = "$foregroundName on $surfaceName",
                    foreground = foreground,
                    background = surface,
                )
            }
        }
    }

    @Test
    fun errorTokensMapToTheIntendedPaletteValues() {
        assertEquals(
            "onError must use the dark text intended for the filled danger surface",
            QrLibreColorValues.OnLime,
            QrLibreDarkColors.onError.argbLong(),
        )
        assertEquals(
            "onErrorContainer must use the readable danger text token",
            QrLibreColorValues.DangerText,
            QrLibreDarkColors.onErrorContainer.argbLong(),
        )
        assertEquals(
            "error itself stays the filled-surface token",
            QrLibreColorValues.Danger,
            QrLibreDarkColors.error.argbLong(),
        )
    }

    private fun assertContrast(label: String, foreground: Long, background: Long) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label must be at least 4.5:1, was ${"%.2f".format(Locale.US, ratio)}:1",
            ratio >= 4.5,
        )
    }
}

private fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * WCAG 2.1 relative-luminance contrast for opaque sRGB colors.
 * Test-only: the shipping app has no contrast engine.
 */
private fun contrastRatio(foreground: Long, background: Long): Double {
    val first = relativeLuminance(foreground)
    val second = relativeLuminance(background)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val value = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
