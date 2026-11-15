package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RampTest {

    private val up = Segment(300, 8, endLevel = 12)
    private val down = Segment(300, 12, endLevel = 8)

    @Test
    fun heldSegmentKeepsItsLevel() {
        val s = Segment(300, 9)
        assertFalse(s.isRamp)
        assertEquals(9, s.levelAt(0))
        assertEquals(9, s.levelAt(299))
    }

    @Test
    fun rampUpStartsAndEndsOnItsLevels() {
        assertTrue(up.isRamp)
        assertEquals(8, up.levelAt(0))
        assertEquals(10, up.levelAt(150))
        assertEquals(12, up.levelAt(300))
    }

    @Test
    fun rampStepsOneLevelAtATime() {
        val levels = (0..300).map { up.levelAt(it) }
        assertEquals(listOf(8, 9, 10, 11, 12), levels.distinct())
        levels.zipWithNext().forEach { (a, b) -> assertTrue(b - a in 0..1) }
    }

    @Test
    fun rampDownMirrorsRampUp() {
        assertEquals(12, down.levelAt(0))
        assertEquals(10, down.levelAt(150))
        assertEquals(8, down.levelAt(300))
        assertEquals(listOf(12, 11, 10, 9, 8), (0..300).map { down.levelAt(it) }.distinct())
    }

    @Test
    fun peakLastsAboutAsLongAsTheMiddleLevels() {
        // Up then down: time at 12 should be about one step (75 s), not two.
        val w = Workout("t", listOf(up, down))
        val secondsAt12 = (0 until w.totalSec).count { w.levelAt(it) == 12 }
        assertTrue("was $secondsAt12", secondsAt12 in 70..80)
    }

    @Test
    fun workoutLevelAtFollowsSegments() {
        val w = Workout("t", listOf(Segment(60, 3), up))
        assertEquals(3, w.levelAt(30))
        assertEquals(8, w.levelAt(60))
        assertEquals(12, w.levelAt(360))
    }

    @Test
    fun rampsAreSavedAndLoaded() {
        val w = listOf(Workout("t", listOf(up, Segment(60, 5)), id = "x"))
        assertEquals(w, WorkoutCodec.decode(WorkoutCodec.encode(w)))
    }

    @Test
    fun addingAfterARampRampsBack() {
        val d = WorkoutDraft(id = "1", name = "A", segments = listOf(SegmentDraft("5", "0", 8, "", endLevel = 12)))
        val added = d.addSegment().segments.last()
        assertEquals(12, added.level)
        assertEquals(8, added.endLevel)
    }

    @Test
    fun draftKeepsRamps() {
        val w = Workout("t", listOf(up), id = "x")
        assertEquals(w, WorkoutDraft.from(w).toWorkout())
    }
}
