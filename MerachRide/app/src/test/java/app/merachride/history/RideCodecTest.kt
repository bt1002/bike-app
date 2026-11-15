package app.merachride.history

import org.junit.Assert.assertEquals
import org.junit.Test

class RideCodecTest {

    @Test
    fun roundTripsRides() {
        val rides = listOf(
            RideRecord("a", 1_794_714_594_000, 1800, 1700, 12_345, 310, 142, 260, 84, 131, 158, "Hill Pyramid"),
            RideRecord("b", 1_794_700_000_000, 600, 0, 0, 0, null, null, null, null, null, null),
        )
        assertEquals(rides, RideCodec.decode(RideCodec.encode(rides)))
    }

    @Test
    fun skipsDamagedEntries() {
        val text = """{"version":1,"rides":[{"id":"x"},{"id":"ok","start":5,"sec":90}]}"""
        val result = RideCodec.decode(text)
        assertEquals(1, result.size)
        assertEquals(90, result[0].durationSec)
    }
}
