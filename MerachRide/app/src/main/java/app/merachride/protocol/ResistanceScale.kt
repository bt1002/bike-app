package app.merachride.protocol

/**
 * Works out whether Indoor Bike Data (0x2AD2) reports resistance in whole levels (8) or in
 * 0.1 units like the Control Point (80). UNVERIFIED on the S29: nobody has looked at the raw value yet.
 *
 * The bike's range is 1..16 in steps of 1 (0x2AD6), so in 0.1 units it can only send
 * 10, 20, …, 160. That makes every raw value unambiguous except 10 (level 1 or level 10):
 *  - raw > 16              → 0.1 units
 *  - raw 1..16, except 10  → whole levels
 *  - raw 10                → can't tell on its own; uses the level the app last set as a hint.
 * The first unambiguous value fixes the unit for the rest of the session.
 */
class ResistanceScale {
    enum class Units { UNKNOWN, LEVELS, TENTHS }

    var unit: Units = Units.UNKNOWN
        private set

    fun reset() {
        unit = Units.UNKNOWN
    }

    /** Converts a raw value to a level, or null if it can't be known yet. [hint] = level the app last set. */
    fun toLevel(raw: Int, hint: Int?): Int? {
        learn(raw)
        if (raw <= 0) return null
        return when (unit) {
            Units.TENTHS -> (raw + 5) / 10
            Units.LEVELS -> raw
            Units.UNKNOWN -> when (hint) { // raw is 10 here
                1 -> 1
                10 -> 10
                else -> null
            }
        }
    }

    private fun learn(raw: Int) {
        if (unit != Units.UNKNOWN) return
        unit = when {
            raw > FtmsProtocol.MAX_LEVEL -> Units.TENTHS
            raw >= FtmsProtocol.MIN_LEVEL && raw != AMBIGUOUS -> Units.LEVELS
            else -> Units.UNKNOWN
        }
    }

    private companion object {
        const val AMBIGUOUS = 10
    }
}
