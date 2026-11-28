package co.asssie.qrlibre.pairing

/**
 * The longest body the speaker reads. Notifications can carry a whole receipt;
 * a shop only needs the beginning of it, and a shorter sentence finishes
 * sooner and overlaps less with the next notice.
 */
private const val MAX_SPOKEN_BODY = 160

/**
 * The sentence the speaker says for a verified [message].
 *
 * The shape is `Aviso de <app>. <body>`, falling back to the title when the
 * body is empty and to just the app when both are blank. It never adds a
 * confirmation: a notification is not a verified payment, and the app cannot
 * state otherwise. Whitespace is collapsed so the engine does not pause on
 * blank lines, and a long body is cut at a word boundary with an ellipsis.
 */
fun spokenText(message: CaptureMessage): String {
    val app = message.app.trim()
    val intro = if (app.isEmpty()) "Aviso." else "Aviso de $app."
    val text = speakable(message.body).ifEmpty { speakable(message.title) }
    return if (text.isEmpty()) intro else "$intro ${truncate(text)}"
}

private fun speakable(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim()

private fun truncate(text: String): String {
    if (text.length <= MAX_SPOKEN_BODY) return text

    val cut = text.take(MAX_SPOKEN_BODY)
    val lastSpace = cut.lastIndexOf(' ')
    // Cutting at a space keeps the last word whole; a single huge token is cut
    // as is rather than dropped entirely.
    val body = if (lastSpace >= MAX_SPOKEN_BODY / 2) cut.take(lastSpace) else cut
    return body.trimEnd() + "…"
}
