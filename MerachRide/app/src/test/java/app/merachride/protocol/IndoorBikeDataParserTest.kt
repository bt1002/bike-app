package app.merachride.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndoorBikeDataParserTest {

    private fun hex(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun speedCadencePower() {
        // flags 0x0044: speed present (bit 0 clear), cadence (bit 2), power (bit 6)
        val d = IndoorBikeDataParser.parse(
            hex(
                0x44, 0x00,
                0xA0, 0x0F, // speed 4000 * 0.01 = 40.00 km/h
                0xB4, 0x00, // cadence 180 * 0.5 = 90 rpm
                0x96, 0x00, // power 150 W
            ),
        )
        assertEquals(40.0, d.speedKmh!!, 0.001)
        assertEquals(90.0, d.cadenceRpm!!, 0.001)
        assertEquals(150, d.powerW)
        assertNull(d.heartRateBpm)
    }

    @Test
    fun resistanceInTenthsIsNormalised() {
        // flags 0x0021: "more data" (no speed) + resistance
        val d = IndoorBikeDataParser.parse(hex(0x21, 0x00, 0x50, 0x00)) // raw 80
        assertEquals(8, d.resistanceLevel)
    }

    @Test
    fun resistanceInWholeLevelsIsKept() {
        val d = IndoorBikeDataParser.parse(hex(0x21, 0x00, 0x08, 0x00))
        assertEquals(8, d.resistanceLevel)
    }

    @Test
    fun distanceEnergyHeartRateElapsed() {
        // flags: more data(0) + distance(4) + energy(8) + HR(9) + elapsed(11) = 0x0B11
        val d = IndoorBikeDataParser.parse(
            hex(
                0x11, 0x0B,
                0x10, 0x27, 0x00, // distance 10000 m
                0x2C, 0x01, // total energy 300 kcal
                0x00, 0x00, // energy per hour
                0x00, // energy per minute
                0x8C, // HR 140
                0x10, 0x0E, // elapsed 3600 s
            ),
        )
        assertEquals(10_000, d.totalDistanceM)
        assertEquals(300, d.totalEnergyKcal)
        assertEquals(140, d.heartRateBpm)
        assertEquals(3600, d.elapsedTimeS)
    }

    @Test
    fun mergesSplitPacketsWithPreviousValues() {
        val first = IndoorBikeDataParser.parse(hex(0x44, 0x00, 0xA0, 0x0F, 0xB4, 0x00, 0x96, 0x00))
        val second = IndoorBikeDataParser.parse(hex(0x21, 0x00, 0x50, 0x00), first)
        assertEquals(150, second.powerW)
        assertEquals(8, second.resistanceLevel)
    }

    @Test
    fun truncatedPacketKeepsWhatWasParsed() {
        // Claims speed + power but is cut off after speed.
        val d = IndoorBikeDataParser.parse(hex(0x40, 0x00, 0xA0, 0x0F, 0x96))
        assertEquals(40.0, d.speedKmh!!, 0.001)
        assertNull(d.powerW)
    }
}
