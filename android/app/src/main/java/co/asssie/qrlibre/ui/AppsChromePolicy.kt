package co.asssie.qrlibre.ui

internal enum class AppsChrome {
    FULL,
    COMPACT,
    SEARCH_ONLY,
}

/** Below this height the long header and fixed summary would crowd out rows. */
private const val COMPACT_BELOW_HEIGHT_DP = 360f

/** From this system font scale the chrome must compact even in portrait. */
private const val COMPACT_AT_FONT_SCALE = 1.5f

internal fun resolveAppsChrome(
    imeVisible: Boolean,
    viewportHeightDp: Float,
    fontScale: Float,
): AppsChrome = when {
    // The keyboard is the strongest signal: the search field must keep a
    // scrollable list, so all non-essential chrome goes away first.
    imeVisible -> AppsChrome.SEARCH_ONLY
    viewportHeightDp < COMPACT_BELOW_HEIGHT_DP || fontScale >= COMPACT_AT_FONT_SCALE ->
        AppsChrome.COMPACT
    else -> AppsChrome.FULL
}
