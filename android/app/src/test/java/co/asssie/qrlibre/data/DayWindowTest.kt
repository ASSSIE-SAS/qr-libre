package co.asssie.qrlibre.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayWindowTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(local: String, zone: ZoneId): Long =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun hours(count: Long): Long = Duration.ofHours(count).toMillis()

    @Test
    fun startIsInclusiveAndThePreviousMillisecondIsOutside() {
        val range = localDayRange(at("2026-11-28T12:00:00", bogota), bogota)
        val start = at("2026-11-28T00:00:00", bogota)
        val lastMillisecondOfTheDay = at("2026-11-28T23:59:59.999", bogota)
        val lastMillisecondOfYesterday = at("2026-11-27T23:59:59.999", bogota)

        assertEquals(start, range.startMs)
        assertTrue("the first millisecond must be inside the day", start >= range.startMs)
        assertTrue(
            "the last millisecond must still be inside the day",
            lastMillisecondOfTheDay >= range.startMs &&
                lastMillisecondOfTheDay < range.endMs,
        )
        assertFalse(
            "the previous day must be outside the range",
            lastMillisecondOfYesterday >= range.startMs,
        )
    }

    @Test
    fun endIsExclusiveAndMatchesTheNextRangeStart() {
        val range = localDayRange(at("2026-11-28T12:00:00", bogota), bogota)
        val next = localDayRange(range.endMs, bogota)
        val nextMidnight = at("2026-11-29T00:00:00", bogota)

        assertEquals(nextMidnight, range.endMs)
        assertEquals(range.endMs, next.startMs)
        assertTrue("the range must have a positive length", range.endMs > range.startMs)
    }

    @Test
    fun monthAndYearRolloverEndsOnTheNextFirstOfJanuary() {
        val range = localDayRange(at("2026-12-31T23:30:00", bogota), bogota)

        assertEquals(at("2026-12-31T00:00:00", bogota), range.startMs)
        assertEquals(at("2027-01-01T00:00:00", bogota), range.endMs)
    }

    @Test
    fun colombianZoneKeepsTwentyFourHourDaysYearRound() {
        val dates = listOf(
            "2026-03-08T12:00:00",
            "2026-11-01T12:00:00",
            "2026-11-28T12:00:00",
            "2026-12-31T12:00:00",
        )

        for (date in dates) {
            val range = localDayRange(at(date, bogota), bogota)
            assertEquals(
                "Colombia has no daylight saving time on $date",
                hours(24),
                range.endMs - range.startMs,
            )
        }
    }

    @Test
    fun midnightDelayIsPositiveAndNeverZero() {
        val midnight = at("2026-11-28T00:00:00", bogota)
        val lastMillisecond = at("2026-11-28T23:59:59.999", bogota)

        assertEquals(hours(24), millisUntilNextLocalMidnight(midnight, bogota))
        assertEquals(1L, millisUntilNextLocalMidnight(lastMillisecond, bogota))
        assertTrue(millisUntilNextLocalMidnight(midnight, bogota) > 0L)
        assertTrue(millisUntilNextLocalMidnight(lastMillisecond, bogota) > 0L)
    }

    @Test
    fun distanceToNextMidnightNeverExceedsTwentyFiveHours() {
        val samples = listOf(
            "2026-11-28T00:00:00" to bogota,
            "2026-11-28T12:00:00" to bogota,
            "2026-12-31T23:59:59.999" to bogota,
            "2026-03-08T00:00:00" to newYork,
            "2026-03-08T23:59:59.999" to newYork,
            "2026-11-01T00:00:00" to newYork,
            "2026-11-01T12:00:00" to newYork,
        )

        for ((local, zone) in samples) {
            val delay = millisUntilNextLocalMidnight(at(local, zone), zone)
            assertTrue("delay must be positive at $local", delay > 0L)
            assertTrue(
                "delay must be at most 25h at $local, was $delay",
                delay <= hours(25),
            )
        }
    }

    @Test
    fun dstDaysKeepLocalMidnightBoundaries() {
        val spring = localDayRange(at("2026-03-08T12:00:00", newYork), newYork)
        assertEquals(at("2026-03-08T00:00:00", newYork), spring.startMs)
        assertEquals(at("2026-03-09T00:00:00", newYork), spring.endMs)
        assertEquals(hours(23), spring.endMs - spring.startMs)
        assertEquals(
            hours(23),
            millisUntilNextLocalMidnight(spring.startMs, newYork),
        )

        val fall = localDayRange(at("2026-11-01T12:00:00", newYork), newYork)
        assertEquals(at("2026-11-01T00:00:00", newYork), fall.startMs)
        assertEquals(at("2026-11-02T00:00:00", newYork), fall.endMs)
        assertEquals(hours(25), fall.endMs - fall.startMs)
        assertEquals(
            hours(25),
            millisUntilNextLocalMidnight(fall.startMs, newYork),
        )
    }
}
