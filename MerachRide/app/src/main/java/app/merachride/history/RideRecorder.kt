package app.merachride.history

import app.merachride.protocol.BikeData

/** One saved ride. Averages are null when the bike never reported that value. */
data class RideRecord(
    val id: String,
    val startEpochMs: Long,
    /** Seconds connected to the bike during the ride (time spent disconnected isn't counted). */
    val durationSec: Int,
    /** Seconds with cadence above 0. */
    val movingSec: Int,
    val distanceM: Int,
    val energyKcal: Int,
    val avgPowerW: Int?,
    val maxPowerW: Int?,
    val avgCadenceRpm: Int?,
    val avgHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?,
    val workoutName: String?,
)

/**
 * Builds a [RideRecord] from one sample of bike data per second.
 *
 * Distance and energy come from the bike as running totals, which can restart from 0 (for
 * example after the bike sleeps or pauses). So the recorder adds up the increases between
 * samples, and treats a drop as a restart: the new value then counts as distance since the restart.
 * Power, cadence and heart rate averages only use seconds when the rider is pedalling.
 */
class RideRecorder(private val id: String, private val startEpochMs: Long) {
    private var durationSec = 0
    private var movingSec = 0
    private val distance = Counter()
    private val energy = Counter()
    private var powerSum = 0L
    private var powerCount = 0
    private var maxPower: Int? = null
    private var cadenceSum = 0.0
    private var cadenceCount = 0
    private var hrSum = 0L
    private var hrCount = 0
    private var maxHr: Int? = null

    /** Name of the workout ridden, if any (the last one that was running). */
    var workoutName: String? = null

    val elapsedSec: Int get() = durationSec
    val distanceM: Int get() = distance.total
    val energyKcal: Int get() = energy.total

    fun tick(d: BikeData) {
        durationSec++
        distance.add(d.totalDistanceM)
        energy.add(d.totalEnergyKcal)

        val pedalling = (d.cadenceRpm ?: 0.0) > 0.0
        if (pedalling) {
            movingSec++
            d.cadenceRpm?.let {
                cadenceSum += it
                cadenceCount++
            }
            d.powerW?.takeIf { it >= 0 }?.let {
                powerSum += it
                powerCount++
                maxPower = maxOf(maxPower ?: 0, it)
            }
        }
        d.heartRateBpm?.takeIf { it > 0 }?.let {
            hrSum += it
            hrCount++
            maxHr = maxOf(maxHr ?: 0, it)
        }
    }

    fun record() = RideRecord(
        id = id,
        startEpochMs = startEpochMs,
        durationSec = durationSec,
        movingSec = movingSec,
        distanceM = distance.total,
        energyKcal = energy.total,
        avgPowerW = if (powerCount > 0) (powerSum / powerCount).toInt() else null,
        maxPowerW = maxPower,
        avgCadenceRpm = if (cadenceCount > 0) Math.round(cadenceSum / cadenceCount).toInt() else null,
        avgHeartRateBpm = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
        maxHeartRateBpm = maxHr,
        workoutName = workoutName,
    )

    /** Sums the increases of a running total that may restart from 0. */
    private class Counter {
        var total = 0
            private set
        private var last: Int? = null

        fun add(current: Int?) {
            if (current == null || current < 0) return
            val prev = last
            if (prev != null) total += if (current >= prev) current - prev else current
            last = current
        }
    }
}
