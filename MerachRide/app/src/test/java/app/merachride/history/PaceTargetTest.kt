package app.merachride.history

import app.merachride.history.PaceTarget.Trend
import app.merachride.workout.Segment
import app.merachride.workout.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceTargetTest {

    private val ramps = Workout("8 / 12 Ramps", listOf(Segment(300, 8, endLevel = 12)))
    private val custom = Workout("Mine", listOf(Segment(60, 5)), id = "abc")

    private fun ride(
        start: Long,
        key: String?,
        speed: Double?,
        hr: Int?,
        name: String? = null,
    ) = RideRecord(
        id = "r$start", startEpochMs = start, durationSec = 600, movingSec = 600, distanceM = 0, energyKcal = 0,
        avgPowerW = null, maxPowerW = null, avgCadenceRpm = null, avgHeartRateBpm = hr, maxHeartRateBpm = null,
        workoutName = name, avgSpeedKmh = speed, workoutKey = key,
    )

    @Test
    fun builtInKeyUsesTheNameCustomUsesTheId() {
        assertEquals("builtin:8 / 12 Ramps", ramps.key)
        assertEquals("abc", custom.key)
    }

    @Test
    fun averagesTheLastFiveRidesOfTheSameWorkout() {
        val history = listOf(
            ride(7, ramps.key, 30.0, 140),
            ride(6, ramps.key, 28.0, 130),
            ride(5, custom.key, 99.0, 199), // Other workout: ignored.
            ride(4, ramps.key, 26.0, 120),
            ride(3, ramps.key, 24.0, 110),
            ride(2, ramps.key, 22.0, 100),
            ride(1, ramps.key, 10.0, 50), // Sixth oldest: ignored.
        )
        val t = PaceTarget.forWorkout(history, ramps)!!
        assertEquals(5, t.rideCount)
        assertEquals(26.0, t.avgSpeedKmh!!, 1e-9)
        assertEquals(120, t.avgHeartRateBpm)
    }

    @Test
    fun usesNewestRidesEvenIfTheListIsUnordered() {
        val history = (1L..7L).map { ride(it, ramps.key, it.toDouble(), null) }.shuffled()
        assertEquals(5.0, PaceTarget.forWorkout(history, ramps)!!.avgSpeedKmh!!, 1e-9) // Rides 3..7.
    }

    @Test
    fun oldRidesWithoutAKeyMatchByName() {
        val history = listOf(ride(1, key = null, speed = 25.0, hr = null, name = "8 / 12 Ramps"))
        assertEquals(25.0, PaceTarget.forWorkout(history, ramps)!!.avgSpeedKmh!!, 1e-9)
    }

    @Test
    fun missingValuesAreLeftOutOfTheirAverage() {
        val history = listOf(ride(2, ramps.key, 30.0, null), ride(1, ramps.key, null, 120))
        val t = PaceTarget.forWorkout(history, ramps)!!
        assertEquals(30.0, t.avgSpeedKmh!!, 1e-9)
        assertEquals(120, t.avgHeartRateBpm)
        assertEquals(2, t.rideCount)
    }

    @Test
    fun noPastRidesMeansNoTarget() {
        assertNull(PaceTarget.forWorkout(listOf(ride(1, custom.key, 20.0, 100)), ramps))
        assertNull(PaceTarget.forWorkout(listOf(ride(1, ramps.key, null, null)), ramps))
    }

    @Test
    fun trendUsesATwoPercentBand() {
        assertEquals(Trend.AHEAD, PaceTarget.trend(26.0, 25.0))
        assertEquals(Trend.BEHIND, PaceTarget.trend(24.0, 25.0))
        assertEquals(Trend.ON_PACE, PaceTarget.trend(25.4, 25.0))
        assertEquals(Trend.ON_PACE, PaceTarget.trend(24.6, 25.0))
        assertNull(PaceTarget.trend(null, 25.0))
        assertNull(PaceTarget.trend(25.0, null))
    }

    @Test
    fun recorderTracksAverageSpeedWhilePedalling() {
        val r = RideRecorder("id", 0)
        r.tick(app.merachride.protocol.BikeData(cadenceRpm = 80.0, speedKmh = 30.0))
        r.tick(app.merachride.protocol.BikeData(cadenceRpm = 80.0, speedKmh = 20.0))
        r.tick(app.merachride.protocol.BikeData(cadenceRpm = 0.0, speedKmh = 0.0)) // Coasting: left out.
        assertEquals(25.0, r.avgSpeedKmh!!, 1e-9)
        assertEquals(25.0, r.record().avgSpeedKmh!!, 1e-9)
    }

    @Test
    fun codecKeepsSpeedAndKey() {
        val rides = listOf(ride(1, ramps.key, 27.5, 130))
        assertEquals(rides, RideCodec.decode(RideCodec.encode(rides)))
    }
}
