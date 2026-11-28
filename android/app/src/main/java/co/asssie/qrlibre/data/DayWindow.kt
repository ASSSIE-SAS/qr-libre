package co.asssie.qrlibre.data

import java.time.Instant
import java.time.ZoneId

/**
 * Half-open window for one local calendar day: [startMs, endMs), where
 * [endMs] is the next local midnight. Resolving the boundaries through the
 * local date keeps the window correct across month/year rollover and in zones
 * with daylight saving time (a local day can be 23, 24 or 25 hours long).
 */
data class LocalDayRange(
    val startMs: Long,
    val endMs: Long,
)

fun localDayRange(
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDayRange {
    val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return LocalDayRange(
        startMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

/**
 * Milliseconds until the next local midnight. The result is always at least
 * one, so a caller can never arm a zero or negative delay and spin.
 */
fun millisUntilNextLocalMidnight(
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long = (localDayRange(nowMs, zone).endMs - nowMs).coerceAtLeast(1L)
