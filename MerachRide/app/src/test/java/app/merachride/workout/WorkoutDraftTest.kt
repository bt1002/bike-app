package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutDraftTest {

    @Test
    fun segmentDurationFromMinutesAndSeconds() {
        assertEquals(90, SegmentDraft(minutes = "1", seconds = "30").durationSec)
        assertEquals(45, SegmentDraft(minutes = "", seconds = "45").durationSec)
        assertNull(SegmentDraft(minutes = "0", seconds = "0").durationSec)
        assertNull(SegmentDraft(minutes = "1", seconds = "60").durationSec)
        assertNull(SegmentDraft(minutes = "x", seconds = "0").durationSec)
    }

    @Test
    fun needsANameAndValidSegments() {
        assertNotNull(WorkoutDraft(id = "1").problem) // No name.
        assertNotNull(WorkoutDraft(id = "1", name = "A", segments = emptyList()).problem)
        assertNotNull(WorkoutDraft(id = "1", name = "A", segments = listOf(SegmentDraft(minutes = "0", seconds = "0"))).problem)
        assertNull(WorkoutDraft(id = "1", name = "A").problem)
    }

    @Test
    fun toWorkoutTrimsAndKeepsId() {
        val w = WorkoutDraft(id = "abc", name = "  Hills ", segments = listOf(SegmentDraft("2", "0", 7, " Climb "))).toWorkout()!!
        assertEquals("abc", w.id)
        assertEquals("Hills", w.name)
        assertEquals(Segment(120, 7, "Climb"), w.segments.single())
    }

    @Test
    fun moveAndRemoveSegments() {
        val d = WorkoutDraft(id = "1", name = "A", segments = listOf(seg(1), seg(2), seg(3)))
        assertEquals(listOf(2, 1, 3), d.move(0, +1).segments.map { it.level })
        assertEquals(listOf(1, 3, 2), d.move(2, -1).segments.map { it.level })
        assertEquals(d, d.move(0, -1)) // Can't move above the top.
        assertEquals(listOf(1, 3), d.remove(1).segments.map { it.level })
    }

    @Test
    fun addSegmentCopiesTheLastOneWithoutItsLabel() {
        val d = WorkoutDraft(id = "1", name = "A", segments = listOf(SegmentDraft("1", "0", 12, "Hard")))
        assertEquals(SegmentDraft("1", "0", 12, ""), d.addSegment().segments.last())
    }

    @Test
    fun roundTripsThroughAWorkout() {
        val w = Workout("X", listOf(Segment(75, 3, "a"), Segment(600, 10)), id = "id")
        assertEquals(w, WorkoutDraft.from(w).toWorkout())
    }

    private fun seg(level: Int) = SegmentDraft(level = level)
}
