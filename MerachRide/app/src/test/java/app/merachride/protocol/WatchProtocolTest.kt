package app.merachride.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchProtocolTest {
    @Test
    fun bpmRoundTrips() {
        for (bpm in listOf(20, 61, 142, 250)) {
            assertEquals(bpm, WatchProtocol.decodeBpm(WatchProtocol.encodeBpm(bpm)))
        }
    }

    @Test
    fun encodesAsAsciiDigits() {
        assertArrayEquals(byteArrayOf('1'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()), WatchProtocol.encodeBpm(142))
    }

    @Test
    fun rejectsMissingOrDamagedPayloads() {
        assertNull(WatchProtocol.decodeBpm(null))
        assertNull(WatchProtocol.decodeBpm(ByteArray(0)))
        assertNull(WatchProtocol.decodeBpm("abc".toByteArray()))
        assertNull(WatchProtocol.decodeBpm("1420".toByteArray())) // Too long.
        assertNull(WatchProtocol.decodeBpm("-5".toByteArray()))
    }

    @Test
    fun rejectsImplausibleHeartRates() {
        assertNull(WatchProtocol.decodeBpm("0".toByteArray()))
        assertNull(WatchProtocol.decodeBpm("19".toByteArray()))
        assertNull(WatchProtocol.decodeBpm("251".toByteArray()))
    }

    @Test
    fun watchGivesUpWellAfterTheNextPing() {
        // The watch must not stop between two normal pings, even if a few are lost.
        assert(WatchProtocol.WATCH_TIMEOUT_MS >= 4 * WatchProtocol.PING_INTERVAL_MS)
    }
}
