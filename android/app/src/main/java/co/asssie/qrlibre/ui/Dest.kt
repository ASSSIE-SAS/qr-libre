package co.asssie.qrlibre.ui

/**
 * Every surface the app can show. Historial is the product screen and the only
 * root. Settings, Apps and Privacy are one level deep; Pairing lives under
 * Settings, so back there returns to Settings instead of jumping home.
 */
internal enum class Dest {
    HISTORY,
    SETTINGS,
    PAIRING,
    APPS,
    PRIVACY,
}

/**
 * Destination reached by system back. A null result means the shell has
 * nothing older to show, so the activity is allowed to finish.
 */
internal fun backDestination(current: Dest): Dest? = when (current) {
    Dest.HISTORY -> null
    Dest.PAIRING -> Dest.SETTINGS
    Dest.SETTINGS, Dest.APPS, Dest.PRIVACY -> Dest.HISTORY
}

/**
 * The gear is the single visible entry to Settings, so it always resolves to
 * that screen. Tapping it again cannot stack a second copy.
 */
internal fun gearDestination(current: Dest): Dest = when (current) {
    Dest.HISTORY, Dest.SETTINGS, Dest.PAIRING, Dest.APPS, Dest.PRIVACY -> Dest.SETTINGS
}
