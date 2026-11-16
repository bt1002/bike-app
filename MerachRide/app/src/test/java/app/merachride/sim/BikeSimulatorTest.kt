package app.merachride.sim

import app.merachride.protocol.IndoorBikeDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BikeSimulatorTest {

    @Test
    fun encoderRoundTripsThroughTheRealParser() {
        val bytes = IndoorBikeDataEncoder.encode(
            speedKmh = 25.67, cadenceRpm = 84.5, distanceM = 70_000, resistanceLevel = 12,
            powerW = 180, energyKcal = 321, heartRateBpm = 142, elapsedSec = 1_234,
        )
        val d = IndoorBikeDataParser.parse(bytes)
        assertEquals(25.67, d.speedKmh!!, 1e-9)
        assertEquals(84.5, d.cadenceRpm!!, 1e-9)
        assertEquals(70_000, d.totalDistanceM)
        assertEquals(12, d.resistanceRaw)
        assertEquals(180, d.powerW)
        assertEquals(321, d.totalEnergyKcal)
        assertEquals(142, d.heartRateBpm)
        assertEquals(1_234, d.elapsedTimeS)
    }

    @Test
    fun reportsTheLevelItWasSetTo() {
        val sim = BikeSimulator(Random(1))
        sim.setLevel(9)
        assertEquals(9, IndoorBikeDataParser.parse(sim.tick()).resistanceRaw)
        sim.pressButton(+1)
        assertEquals(10, IndoorBikeDataParser.parse(sim.tick()).resistanceRaw)
        sim.setLevel(99)
        assertEquals(16, sim.level)
    }

    @Test
    fun higherLevelMeansMorePower() {
        fun steadyPower(level: Int): Int {
            val sim = BikeSimulator(Random(1))
            sim.setLevel(level)
            repeat(20) { sim.tick() }
            return IndoorBikeDataParser.parse(sim.tick()).powerW!!
        }
        assertTrue(steadyPower(12) > steadyPower(4))
    }

    @Test
    fun stoppingPedallingWindsDown() {
        val sim = BikeSimulator(Random(1))
        repeat(10) { sim.tick() }
        sim.pedalling = false
        repeat(30) { sim.tick() }
        val d = IndoorBikeDataParser.parse(sim.tick())
        assertEquals(0.0, d.cadenceRpm!!, 1e-9)
        assertEquals(0, d.powerW)
    }

    @Test
    fun countersGrowAndCanRestart() {
        val sim = BikeSimulator(Random(1))
        repeat(60) { sim.tick() }
        val before = IndoorBikeDataParser.parse(sim.tick())
        assertTrue(before.totalDistanceM!! > 0)
        sim.resetCounters()
        val after = IndoorBikeDataParser.parse(sim.tick())
        assertTrue(after.totalDistanceM!! < before.totalDistanceM!!)
        assertEquals(1, after.elapsedTimeS)
    }
}
