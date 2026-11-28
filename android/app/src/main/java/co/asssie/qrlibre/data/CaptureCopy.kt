package co.asssie.qrlibre.data

/**
 * Builds the clipboard payload for one capture.
 *
 * The header carries the app label, the package name and the timestamp. A
 * non-empty title and a non-empty body follow as their own sections, separated
 * by exactly one blank line. The body is appended verbatim: its internal and
 * edge line breaks are data, not separators, so they are never trimmed or
 * normalised.
 */
fun captureCopyText(
    app: String,
    packageName: String,
    title: String,
    body: String,
    timestamp: String,
): String {
    val header = listOf(app, packageName, timestamp)
        .filter { it.isNotEmpty() }
        .joinToString("\n")
    val sections = mutableListOf<String>()
    if (header.isNotEmpty()) sections.add(header)
    if (title.isNotEmpty()) sections.add(title)
    if (body.isNotEmpty()) sections.add(body)
    return sections.joinToString("\n\n")
}
