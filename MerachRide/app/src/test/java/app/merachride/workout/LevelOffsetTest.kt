package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelOffsetTest {

    private val w = Workout("t", listOf(Segment(60, 8), Segment(300, 6, endLevel = 12)))

    @Test
    fun zeroOffsetIsThePlainLevel() {
        assertEquals(w.levelAt(30), w.levelAt(30, 0))
        assertEquals(w.levelAt(200), w.levelAt(200, 0))
    }

    @Test
    fun offsetShiftsHeldAndRampSegments() {
        assertEquals(9, w.levelAt(30, +1))
        assertEquals(6, w.levelAt(30, -2))
        assertEquals(7, w.levelAt(60, +1)) // Ramp start 6 -> 7.
        assertEquals(13, w.levelAt(360, +1)) // Ramp end 12 -> 13.
    }

    @Test
    fun offsetIsClampedToTheBikesRange() {
        assertEquals(16, w.levelAt(360, +10))
        assertEquals(1, w.levelAt(30, -20))
    }
}
