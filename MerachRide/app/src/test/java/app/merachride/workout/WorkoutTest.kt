package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTest {

    private val w = Workout(
        "Test",
        listOf(Segment(60, 3, "A"), Segment(30, 10, "B"), Segment(90, 5, "C")),
    )

    @Test
    fun totalDuration() {
        assertEquals(180, w.totalSec)
    }

    @Test
    fun segmentBoundaries() {
        assertEquals(0, w.segmentIndexAt(0))
        assertEquals(0, w.segmentIndexAt(59))
        assertEquals(1, w.segmentIndexAt(60))
        assertEquals(1, w.segmentIndexAt(89))
        assertEquals(2, w.segmentIndexAt(90))
        assertEquals(2, w.segmentIndexAt(500)) // past the end -> last
    }

    @Test
    fun segmentStarts() {
        assertEquals(0, w.segmentStart(0))
        assertEquals(60, w.segmentStart(1))
        assertEquals(90, w.segmentStart(2))
    }

    @Test
    fun stateDerivedValues() {
        val s = WorkoutState(workout = w, elapsedSec = 70)
        assertEquals(1, s.segmentIndex)
        assertEquals(20, s.segmentRemainingSec)
        assertEquals(110, s.totalRemainingSec)
        assertEquals("C", s.nextSegment?.label)
    }

    @Test
    fun noNextSegmentWhenFinished() {
        val s = WorkoutState(workout = w, elapsedSec = 180, finished = true)
        assertNull(s.nextSegment)
        assertEquals(0, s.totalRemainingSec)
    }

    @Test
    fun sampleWorkoutsUseValidLevels() {
        SampleWorkouts.all.forEach { wk ->
            wk.segments.forEach { seg -> assertTrue("${wk.name}: level ${seg.level}", seg.level in 1..16) }
        }
    }
}
