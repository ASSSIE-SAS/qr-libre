package co.asssie.qrlibre.data

data class Capture(
    val id: Long,
    val packageName: String,
    val app: String,
    val title: String,
    val body: String,
    val epochMs: Long,
    val verdict: Verdict? = null,
)
