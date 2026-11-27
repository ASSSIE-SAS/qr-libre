package co.asssie.qrlibre.notifications

/**
 * Makes notification text predictable enough to store and inspect.
 *
 * - trims every line,
 * - collapses internal whitespace runs into a single space,
 * - drops lines that end up empty,
 * - returns an empty string for null or blank input.
 *
 * Lines are kept separate so a title/body pair from the bank is readable.
 */
fun normalizeNotificationText(raw: String?): String {
    if (raw == null) return ""
    return raw.lineSequence()
        .map { line -> line.replace(WHITESPACE_RUN, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

// ASCII whitespace plus the no-break spaces common in bank SMS and pushes.
private val WHITESPACE_RUN = Regex("[\\s\\u00A0\\u2007\\u202F]+")
