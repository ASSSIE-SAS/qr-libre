package co.asssie.qrlibre.ui

/**
 * Every surface the app can show. Historial is the product screen and the
 * only root; the rest are one level deep and always return to it.
 */
internal enum class Dest {
    HISTORY,
    SETTINGS,
    APPS,
    PRIVACY,
}

/**
 * Destination reached by system back. A null result means the shell has
 * nothing older to show, so the activity is allowed to finish.
 */
internal fun backDestination(current: Dest): Dest? = when (current) {
    Dest.HISTORY -> null
    Dest.SETTINGS, Dest.APPS, Dest.PRIVACY -> Dest.HISTORY
}

/**
 * The gear is the single visible entry to Settings, so it always resolves to
 * that screen. Tapping it again cannot stack a second copy.
 */
internal fun gearDestination(current: Dest): Dest = when (current) {
    Dest.HISTORY, Dest.SETTINGS, Dest.APPS, Dest.PRIVACY -> Dest.SETTINGS
}
