package app.merachride.sim

import app.merachride.protocol.FtmsProtocol
import kotlin.math.pow
import kotlin.random.Random

/**
 * A pretend S29 for testing without the bike (debug builds only). Takes level commands like the real
 * bike and produces one Indoor Bike Data (0x2AD2) packet per tick in the same format, so the app's
 * parser, resistance handling, workouts and ride recording all run as they would on the real bike.
 *
 * The numbers are believable, not accurate: power grows with level and cadence, speed follows cadence.
 * Thread-safe: the session ticks it while the UI presses its buttons.
 */
class BikeSimulator(private val random: Random = Random.Default) {
    var level = 5
        @Synchronized get
        private set

    /** Cadence the pretend rider aims for (rpm). */
    @Volatile var targetCadence = 85.0

    /** False = the rider has stopped pedalling (coasting). */
    @Volatile var pedalling = true

    private var cadence = 0.0
    private var distanceM = 0.0
    private var energyKcal = 0.0
    private var elapsedSec = 0
    private var heartRate = 70.0

    /** A level command from the app (Control Point `04`). */
    @Synchronized
    fun setLevel(l: Int) {
        level = l.coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL)
    }

    /** The rider pressing the bike's own + / − buttons. */
    @Synchronized
    fun pressButton(delta: Int) = setLevel(level + delta)

    /** What the bike does when it sleeps: its running totals start again from 0. */
    @Synchronized
    fun resetCounters() {
        distanceM = 0.0
        energyKcal = 0.0
        elapsedSec = 0
    }

    /** Advances one second and returns that second's Indoor Bike Data packet. */
    @Synchronized
    fun tick(): ByteArray {
        elapsedSec++
        val goal = if (pedalling) targetCadence + random.nextDouble(-3.0, 3.0) else 0.0
        cadence += (goal - cadence) * 0.5 // Spin up and slow down over a few seconds.
        if (cadence < 1.0) cadence = 0.0

        val power = if (cadence > 0.0) ((cadence / 85.0).pow(2) * (15 + 14 * level)) else 0.0
        val speedKmh = cadence * 0.33 * (1 - (level - 1) * 0.012) // A heavier level is a bit slower.
        distanceM += speedKmh / 3.6
        energyKcal += power / 1000.0 // About 1 kcal per kJ of work (body efficiency ~24%).
        heartRate += ((75 + power * 0.4) - heartRate) * 0.05 // Drifts toward the effort.

        return IndoorBikeDataEncoder.encode(
            speedKmh = speedKmh,
            cadenceRpm = cadence,
            distanceM = distanceM.toInt(),
            resistanceLevel = level, // Whole levels, as the real S29 reports.
            powerW = power.toInt(),
            energyKcal = energyKcal.toInt(),
            heartRateBpm = heartRate.toInt(),
            elapsedSec = elapsedSec,
        )
    }
}

/** Builds Indoor Bike Data (0x2AD2) packets; the inverse of [app.merachride.protocol.IndoorBikeDataParser]. */
object IndoorBikeDataEncoder {
    fun encode(
        speedKmh: Double,
        cadenceRpm: Double,
        distanceM: Int,
        resistanceLevel: Int,
        powerW: Int,
        energyKcal: Int,
        heartRateBpm: Int,
        elapsedSec: Int,
    ): ByteArray {
        // Bit 0 clear = speed present; then cadence (2), distance (4), resistance (5), power (6),
        // energy (8), heart rate (9) and elapsed time (11).
        val flags = (1 shl 2) or (1 shl 4) or (1 shl 5) or (1 shl 6) or (1 shl 8) or (1 shl 9) or (1 shl 11)
        val out = ArrayList<Byte>(24)
        fun u8(v: Int) = out.add((v and 0xFF).toByte())
        fun u16(v: Int) {
            u8(v)
            u8(v shr 8)
        }
        u16(flags)
        u16(Math.round(speedKmh * 100).toInt()) // 0.01 km/h
        u16(Math.round(cadenceRpm * 2).toInt()) // 0.5 rpm
        u16(distanceM)
        u8(distanceM shr 16) // uint24
        u16(resistanceLevel) // sint16
        u16(powerW) // sint16
        u16(energyKcal) // total
        u16(0xFFFF) // per hour: not available
        u8(0xFF) // per minute: not available
        u8(heartRateBpm)
        u16(elapsedSec)
        return out.toByteArray()
    }
}
