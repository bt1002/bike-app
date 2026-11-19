package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextLevelChangeTest {
    // 60 s at 8, a 300 s ramp 8 → 12 (steps at 38/113/188/263 s into it), then 60 s at 12, then 60 s at 6.
    private val w = Workout(
        "Test",
        listOf(Segment(60, 8), Segment(300, 8, endLevel = 12), Segment(60, 12), Segment(60, 6)),
    )

    @Test
    fun skipsASegmentThatStartsAtTheSameLevel() {
        // Held 8, then the ramp also starts at 8: the first change is the ramp's first step at 60 + 38.
        assertEquals(LevelChange(98, 9), w.nextLevelChange(0, 0))
        assertEquals(LevelChange(1, 9), w.nextLevelChange(97, 0))
    }

    @Test
    fun countsDownToEachRampStep() {
        assertEquals(LevelChange(75, 10), w.nextLevelChange(98, 0)) // 9 → 10 at 60 + 113.
        assertEquals(LevelChange(10, 10), w.nextLevelChange(163, 0))
    }

    @Test
    fun rampEndingAtTheNextSegmentsLevelRunsOnToTheNextRealChange() {
        // 12 from 60 + 263 = 323 through the held-12 segment until 420, where it drops to 6.
        assertEquals(LevelChange(97, 6), w.nextLevelChange(323, 0))
    }

    @Test
    fun noChangeBeforeTheEndIsNull() {
        assertNull(w.nextLevelChange(420, 0))
        assertNull(w.nextLevelChange(479, 0))
    }

    @Test
    fun usesTheRidersOffsetIncludingClamping() {
        assertEquals(LevelChange(98, 11), w.nextLevelChange(0, 2))
        // With +5, the ramp's top steps all clamp to 16: 8+5=13, 14, 15, 16, 16 → the 16→16 step isn't a change.
        assertEquals(LevelChange(97 + 75, 11), w.nextLevelChange(323 - 75, 5)) // From 16 (at 248) to 6+5 = 11 at 420.
    }

    @Test
    fun stateExposesItWhileRunningButNotWhenFinished() {
        assertEquals(LevelChange(98, 9), WorkoutState(workout = w).nextLevelChange)
        assertNull(WorkoutState(workout = w, finished = true).nextLevelChange)
        assertNull(WorkoutState().nextLevelChange)
    }
}
