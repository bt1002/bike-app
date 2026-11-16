package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelOffsetTest {
    // 60 s at 8, then a 300 s ramp 8 → 12, then 60 s at 15.
    private val w = Workout(
        "Test",
        listOf(Segment(60, 8), Segment(300, 8, endLevel = 12), Segment(60, 15)),
    )

    @Test
    fun noOffsetIsTheProgram() {
        for (t in listOf(0, 59, 60, 200, 359, 360, 419)) assertEquals(w.levelAt(t), w.adjustedLevelAt(t, 0))
    }

    @Test
    fun offsetShiftsLaterSegmentsAndRamps() {
        assertEquals(10, w.adjustedLevelAt(0, 2))
        assertEquals(10, w.adjustedLevelAt(60, 2)) // Ramp start 8 + 2.
        assertEquals(14, w.adjustedLevelAt(359, 2)) // Ramp end 12 + 2.
        assertEquals(7, w.adjustedLevelAt(359, -5))
    }

    @Test
    fun adjustedLevelsStayWithinTheBikesRange() {
        assertEquals(16, w.adjustedLevelAt(360, 3)) // 15 + 3 → 16.
        assertEquals(1, w.adjustedLevelAt(0, -10)) // 8 − 10 → 1.
    }

    @Test
    fun offsetForMatchesTheLevelTheRiderChose() {
        assertEquals(2, w.offsetFor(10, 0)) // Program 8, rider at 10.
        assertEquals(-3, w.offsetFor(5, 0))
        assertEquals(0, w.offsetFor(8, 30))
        // 200 s into the 8 → 12 ramp the program is 11; the rider sets 13, so +2 from then on.
        val t = 60 + 200
        assertEquals(11, w.levelAt(t))
        assertEquals(2, w.offsetFor(13, t))
        assertEquals(14, w.adjustedLevelAt(359, w.offsetFor(13, t)))
    }

    @Test
    fun riderLevelIsClampedBeforeWorkingOutTheOffset() {
        assertEquals(1, w.offsetFor(20, 360)) // Program 15; 20 is really 16.
    }

    @Test
    fun levelTextShowsAdjustedLevels() {
        val ramp = Segment(300, 8, endLevel = 12)
        assertEquals("8→12", ramp.levelText(0))
        assertEquals("10→14", ramp.levelText(2))
        assertEquals("16", Segment(60, 15, endLevel = 16).levelText(4)) // Both ends clamp to 16.
        assertEquals("6", Segment(60, 8).levelText(-2))
    }
}
