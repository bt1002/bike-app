package app.merachride.history

import app.merachride.protocol.BikeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideRecorderTest {

    private fun sample(
        rpm: Double? = 80.0,
        watts: Int? = 150,
        distM: Int? = null,
        kcal: Int? = null,
        hr: Int? = null,
    ) = BikeData(cadenceRpm = rpm, powerW = watts, totalDistanceM = distM, totalEnergyKcal = kcal, heartRateBpm = hr)

    @Test
    fun countsDurationAndMovingTime() {
        val r = RideRecorder("id", 1_000)
        repeat(3) { r.tick(sample(rpm = 80.0)) }
        repeat(2) { r.tick(sample(rpm = 0.0, watts = 0)) }
        val rec = r.record()
        assertEquals(5, rec.durationSec)
        assertEquals(3, rec.movingSec)
        assertEquals("id", rec.id)
        assertEquals(1_000, rec.startEpochMs)
    }

    @Test
    fun averagesOnlyWhilePedalling() {
        val r = RideRecorder("id", 0)
        r.tick(sample(rpm = 80.0, watts = 100))
        r.tick(sample(rpm = 90.0, watts = 200))
        r.tick(sample(rpm = 0.0, watts = 0)) // Coasting: not part of the averages.
        val rec = r.record()
        assertEquals(150, rec.avgPowerW)
        assertEquals(200, rec.maxPowerW)
        assertEquals(85, rec.avgCadenceRpm)
    }

    @Test
    fun distanceIsMeasuredFromTheFirstSample() {
        val r = RideRecorder("id", 0)
        r.tick(sample(distM = 5_000)) // The bike's total from before the ride.
        r.tick(sample(distM = 5_010))
        r.tick(sample(distM = 5_025))
        assertEquals(25, r.record().distanceM)
    }

    @Test
    fun counterRestartIsAddedNotSubtracted() {
        val r = RideRecorder("id", 0)
        r.tick(sample(kcal = 40))
        r.tick(sample(kcal = 50)) // +10
        r.tick(sample(kcal = 3)) // Bike restarted its counter: +3
        r.tick(sample(kcal = 7)) // +4
        assertEquals(17, r.record().energyKcal)
    }

    @Test
    fun missingValuesDontBreakCounters() {
        val r = RideRecorder("id", 0)
        r.tick(sample(distM = 100))
        r.tick(sample(distM = null)) // e.g. right after a reconnect.
        r.tick(sample(distM = 130))
        assertEquals(30, r.record().distanceM)
    }

    @Test
    fun heartRateIgnoresZeroAndAveragesTheRest() {
        val r = RideRecorder("id", 0)
        r.tick(sample(hr = 0))
        r.tick(sample(hr = 120))
        r.tick(sample(hr = 140))
        val rec = r.record()
        assertEquals(130, rec.avgHeartRateBpm)
        assertEquals(140, rec.maxHeartRateBpm)
    }

    @Test
    fun nothingReportedGivesNullAverages() {
        val r = RideRecorder("id", 0)
        r.tick(BikeData())
        val rec = r.record()
        assertNull(rec.avgPowerW)
        assertNull(rec.avgCadenceRpm)
        assertNull(rec.avgHeartRateBpm)
        assertEquals(0, rec.distanceM)
    }
}
