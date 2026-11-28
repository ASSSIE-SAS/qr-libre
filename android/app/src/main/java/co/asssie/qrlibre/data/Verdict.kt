package co.asssie.qrlibre.data

/**
 * The owner's classification of a capture, never a bank confirmation.
 *
 * [wire] is the only serialisation form: it is written to the database column
 * and to the export document, so it must stay stable across versions.
 */
enum class Verdict(val wire: String) {
    PAYMENT("payment"),
    NOT_PAYMENT("not_payment"),
    ;

    companion object {
        /** The verdict stored as [value], or null when it is absent or unknown. */
        fun fromWire(value: String?): Verdict? =
            entries.firstOrNull { it.wire == value }
    }
}
