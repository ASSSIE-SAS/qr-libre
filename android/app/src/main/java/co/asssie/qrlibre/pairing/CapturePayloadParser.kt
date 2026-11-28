package co.asssie.qrlibre.pairing

/**
 * A payload longer than this cannot come from our emitter and is refused
 * before parsing. The negotiated MTU is far smaller; the cap only stops a
 * hostile peer from handing us megabytes over one notification.
 */
private const val MAX_PAYLOAD_CHARS = 4096

/** The only verdict tokens the wire contract defines. */
private val KNOWN_VERDICTS = setOf("payment", "not_payment")

/**
 * Parses the JSON payload carried inside a verified capture frame.
 *
 * This is a deliberately small reader for the flat object
 * `encodeCaptureMessage` writes: string, number, boolean and null values, no
 * nested objects or arrays. It exists instead of `org.json` because the parser
 * is part of the JVM test surface and the platform stub is not.
 *
 * Returns null for anything that is not that object with usable
 * `id`, `app`, `title`, `body`, `epoch_ms` and `verdict` fields. Unknown extra
 * keys with primitive values are ignored so a newer emitter can add a field
 * without breaking older receivers. Never throws.
 */
fun decodeCaptureMessage(payload: String): CaptureMessage? {
    if (payload.length > MAX_PAYLOAD_CHARS) return null

    val fields = try {
        JsonReader(payload).readObject()
    } catch (_: IllegalArgumentException) {
        return null
    }

    val id = fields.longValue("id") ?: return null
    val app = fields.stringValue("app") ?: return null
    val title = fields.stringValue("title") ?: return null
    val body = fields.stringValue("body") ?: return null
    val epochMs = fields.longValue("epoch_ms") ?: return null
    val verdict = when (val value = fields["verdict"]) {
        null -> return null
        JsonValue.Null -> null
        is JsonValue.Text -> value.value.takeIf { it in KNOWN_VERDICTS } ?: return null
        else -> return null
    }

    return CaptureMessage(
        id = id,
        app = app,
        title = title,
        body = body,
        epochMs = epochMs,
        verdict = verdict,
    )
}

private fun Map<String, JsonValue>.stringValue(key: String): String? =
    (this[key] as? JsonValue.Text)?.value

private fun Map<String, JsonValue>.longValue(key: String): Long? =
    (this[key] as? JsonValue.Number)?.raw?.toLongOrNull()

/** The four JSON primitives this wire format can contain. */
private sealed interface JsonValue {
    data class Text(val value: String) : JsonValue
    data class Number(val raw: String) : JsonValue
    data class Flag(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/**
 * A single-pass reader. Every malformed input ends in [IllegalArgumentException],
 * which [decodeCaptureMessage] turns into null.
 */
private class JsonReader(private val text: String) {

    private var index = 0

    fun readObject(): Map<String, JsonValue> {
        skipWhitespace()
        expect('{')
        skipWhitespace()

        val values = LinkedHashMap<String, JsonValue>()
        if (peek() == '}') {
            index++
            finish()
            return values
        }

        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            values[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    break
                }

                else -> fail()
            }
        }

        finish()
        return values
    }

    private fun readValue(): JsonValue = when (val ch = peek()) {
        '"' -> JsonValue.Text(readString())
        '{', '[' -> fail()
        'n' -> {
            expectLiteral("null")
            JsonValue.Null
        }

        't' -> {
            expectLiteral("true")
            JsonValue.Flag(true)
        }

        'f' -> {
            expectLiteral("false")
            JsonValue.Flag(false)
        }

        else -> if (ch == '-' || ch in '0'..'9') readNumber() else fail()
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            if (index >= text.length) fail()
            val ch = text[index++]
            when {
                ch == '"' -> return out.toString()
                ch == '\\' -> out.append(readEscape())
                ch < ' ' -> fail()
                else -> out.append(ch)
            }
        }
    }

    private fun readEscape(): Char {
        if (index >= text.length) fail()
        return when (val escaped = text[index++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > text.length) fail()
                val code = text.substring(index, index + 4).toIntOrNull(16) ?: fail()
                index += 4
                code.toChar()
            }

            else -> fail()
        }
    }

    /**
     * Reads an integer. Fractions and exponents are refused: `id` and
     * `epoch_ms` are whole numbers, so a decimal means the frame is not ours.
     */
    private fun readNumber(): JsonValue {
        val start = index
        if (peek() == '-') index++
        if (index >= text.length || text[index] !in '0'..'9') fail()

        if (text[index] == '0') {
            index++
        } else {
            while (index < text.length && text[index] in '0'..'9') index++
        }

        return JsonValue.Number(text.substring(start, index))
    }

    private fun expectLiteral(literal: String) {
        if (!text.startsWith(literal, index)) fail()
        index += literal.length
    }

    private fun expect(ch: Char) {
        if (peek() != ch) fail()
        index++
    }

    private fun peek(): Char = if (index < text.length) text[index] else fail()

    private fun skipWhitespace() {
        while (index < text.length && text[index] in " \t\r\n") index++
    }

    private fun finish() {
        skipWhitespace()
        if (index != text.length) fail()
    }

    private fun fail(): Nothing = throw IllegalArgumentException("malformed payload")
}
