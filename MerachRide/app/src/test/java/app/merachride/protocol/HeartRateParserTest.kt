package app.merachride.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun uint8HeartRate() {
        assertEquals(72, HeartRateParser.parse(bytes(0x00, 72)))
    }

    @Test
    fun uint8AboveSignedByteRange() {
        assertEquals(190, HeartRateParser.parse(bytes(0x00, 190)))
    }

    @Test
    fun uint16HeartRate() {
        assertEquals(300, HeartRateParser.parse(bytes(0x01, 0x2C, 0x01)))
    }

    @Test
    fun ignoresExtraFieldsAfterTheHeartRate() {
        // Flags: uint8 HR + sensor contact + RR intervals present.
        assertEquals(128, HeartRateParser.parse(bytes(0x16, 128, 0x00, 0x04, 0x10, 0x04)))
    }

    @Test
    fun zeroMeansNoReading() {
        assertNull(HeartRateParser.parse(bytes(0x00, 0)))
    }

    @Test
    fun shortPacketsAreIgnored() {
        assertNull(HeartRateParser.parse(bytes()))
        assertNull(HeartRateParser.parse(bytes(0x00)))
        assertNull(HeartRateParser.parse(bytes(0x01, 0x2C)))
    }
}
