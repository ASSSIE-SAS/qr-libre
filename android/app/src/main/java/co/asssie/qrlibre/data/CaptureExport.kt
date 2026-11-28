package co.asssie.qrlibre.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serialises the loaded captures into the export document.
 *
 * The order is the order given: [CapturesDb.recentCaptures] already returns the
 * newest capture first. Each object keeps the same key order so two exports of
 * the same data are byte-identical. The verdict follows `epoch_ms` and is the
 * wire value (`payment`, `not_payment`) or a JSON null when unset. Strings are
 * escaped as JSON requires while every other character, accents and emoji
 * included, is written verbatim. The body is data: its line breaks and blank
 * lines survive as escaped characters, never as separators.
 */
fun exportCaptures(captures: List<Capture>): String {
    if (captures.isEmpty()) return "[]"
    return captures.joinToString(
        separator = ",\n",
        prefix = "[\n",
        postfix = "\n]",
    ) { capture ->
        "  {" +
            "\"package\":${jsonString(capture.packageName)}," +
            "\"app\":${jsonString(capture.app)}," +
            "\"title\":${jsonString(capture.title)}," +
            "\"body\":${jsonString(capture.body)}," +
            "\"epoch_ms\":${capture.epochMs}," +
            "\"verdict\":${jsonVerdict(capture.verdict)}" +
            "}"
    }
}

/** `qr-libre-avisos-YYYY-MM-DD.json`, dated by the local day of [nowMs]. */
fun exportFileName(nowMs: Long): String {
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
    return "qr-libre-avisos-$day.json"
}

/** The verdict token: a quoted wire value or a bare JSON null. */
private fun jsonVerdict(verdict: Verdict?): String =
    if (verdict == null) "null" else jsonString(verdict.wire)

private fun jsonString(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    for (ch in value) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            else -> if (ch < ' ') {
                out.append("\\u")
                out.append(ch.code.toString(16).padStart(4, '0'))
            } else {
                out.append(ch)
            }
        }
    }
    out.append('"')
    return out.toString()
}
