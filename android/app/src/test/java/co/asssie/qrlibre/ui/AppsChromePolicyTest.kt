package co.asssie.qrlibre.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppsChromePolicyTest {

    @Test
    fun imeWinsEvenOnTallViewportsAndLargeFont() {
        assertEquals(
            "the keyboard must collapse apps chrome no matter how tall the viewport is",
            AppsChrome.SEARCH_ONLY,
            resolveAppsChrome(
                imeVisible = true,
                viewportHeightDp = 800f,
                fontScale = 1f,
            ),
        )
        assertEquals(
            "large font must not override the IME precedence",
            AppsChrome.SEARCH_ONLY,
            resolveAppsChrome(
                imeVisible = true,
                viewportHeightDp = 700f,
                fontScale = 2f,
            ),
        )
    }

    @Test
    fun shortLandscapeUsesCompactChrome() {
        assertEquals(
            "a landscape-height viewport must drop the long description and fixed summary",
            AppsChrome.COMPACT,
            resolveAppsChrome(
                imeVisible = false,
                viewportHeightDp = 298f,
                fontScale = 1f,
            ),
        )
    }

    @Test
    fun largeFontFallsBackToCompactEvenInTallPortrait() {
        assertEquals(
            "200% font must compact the chrome to keep rows visible",
            AppsChrome.COMPACT,
            resolveAppsChrome(
                imeVisible = false,
                viewportHeightDp = 700f,
                fontScale = 2f,
            ),
        )
        assertEquals(
            "the large-font threshold starts at 150%",
            AppsChrome.COMPACT,
            resolveAppsChrome(
                imeVisible = false,
                viewportHeightDp = 700f,
                fontScale = 1.5f,
            ),
        )
    }

    @Test
    fun normalPortraitKeepsFullChrome() {
        assertEquals(
            "ample portrait height at the default font keeps the full header",
            AppsChrome.FULL,
            resolveAppsChrome(
                imeVisible = false,
                viewportHeightDp = 800f,
                fontScale = 1f,
            ),
        )
    }
}
