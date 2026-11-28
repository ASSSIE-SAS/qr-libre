package co.asssie.qrlibre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerdictTest {

    @Test
    fun wireValuesAreStable() {
        assertEquals("payment", Verdict.PAYMENT.wire)
        assertEquals("not_payment", Verdict.NOT_PAYMENT.wire)
    }

    @Test
    fun fromWireRoundTripsEveryValue() {
        for (verdict in Verdict.entries) {
            assertEquals(verdict, Verdict.fromWire(verdict.wire))
        }
    }

    @Test
    fun fromWireRejectsNullEmptyAndUnknownValues() {
        assertNull(Verdict.fromWire(null))
        assertNull(Verdict.fromWire(""))
        assertNull(Verdict.fromWire("PAYMENT"))
        assertNull(Verdict.fromWire("payment "))
        assertNull(Verdict.fromWire("not-payment"))
        assertNull(Verdict.fromWire("verified"))
    }
}
