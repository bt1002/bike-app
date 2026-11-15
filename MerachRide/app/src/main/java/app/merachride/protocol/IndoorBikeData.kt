package app.merachride.protocol

/** Latest live values from the bike. Null = the bike hasn't reported that field yet. */
data class BikeData(
    val speedKmh: Double? = null,
    val cadenceRpm: Double? = null,
    val totalDistanceM: Int? = null,
    /** Resistance exactly as the bike sent it. Units unverified: see [ResistanceScale]. */
    val resistanceRaw: Int? = null,
    /** Resistance as a 1..16 level, worked out from [resistanceRaw] by [ResistanceScale]. Null = unknown. */
    val resistanceLevel: Int? = null,
    val powerW: Int? = null,
    val totalEnergyKcal: Int? = null,
    val heartRateBpm: Int? = null,
    val elapsedTimeS: Int? = null,
)

/**
 * Parser for FTMS Indoor Bike Data (0x2AD2) notifications.
 *
 * Each packet starts with a 16-bit flags field saying which fields follow. A bike may
 * split its data over several packets, so each parse merges into the previous values.
 */
object IndoorBikeDataParser {

    fun parse(bytes: ByteArray, previous: BikeData = BikeData()): BikeData {
        if (bytes.size < 2) return previous
        val r = Reader(bytes)
        val flags = r.u16()
        fun has(bit: Int): Boolean = ((flags shr bit) and 1) == 1

        var d = previous
        try {
            // Bit 0 is inverted: "More Data" = 0 means Instantaneous Speed IS present.
            if (!has(0)) d = d.copy(speedKmh = r.u16() / 100.0)
            if (has(1)) r.skip(2)                                   // average speed
            if (has(2)) d = d.copy(cadenceRpm = r.u16() / 2.0)
            if (has(3)) r.skip(2)                                   // average cadence
            if (has(4)) d = d.copy(totalDistanceM = r.u24())
            if (has(5)) d = d.copy(resistanceRaw = r.s16())
            if (has(6)) d = d.copy(powerW = r.s16())
            if (has(7)) r.skip(2)                                   // average power
            if (has(8)) {                                           // expended energy
                val total = r.u16()
                r.skip(3)                                           // per hour (u16) + per minute (u8)
                if (total != 0xFFFF) d = d.copy(totalEnergyKcal = total)
            }
            if (has(9)) d = d.copy(heartRateBpm = r.u8())
            if (has(10)) r.skip(1)                                  // metabolic equivalent
            if (has(11)) d = d.copy(elapsedTimeS = r.u16())
            if (has(12)) r.skip(2)                                  // remaining time
        } catch (e: IndexOutOfBoundsException) {
            // Truncated packet: keep whatever was parsed before the end.
        }
        return d
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0
        fun u8(): Int = b[i++].toInt() and 0xFF
        fun u16(): Int {
            val lo = u8()
            val hi = u8()
            return lo or (hi shl 8)
        }
        fun s16(): Int = u16().toShort().toInt()
        fun u24(): Int {
            val b0 = u8()
            val b1 = u8()
            val b2 = u8()
            return b0 or (b1 shl 8) or (b2 shl 16)
        }
        fun skip(n: Int) {
            if (i + n > b.size) throw IndexOutOfBoundsException()
            i += n
        }
    }
}
