package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelRunsTest {
    @Test
    fun heldLevelIsOneRun() {
        assertEquals(listOf(LevelRun(0, 120, 9)), Segment(120, 9).levelRuns())
    }

    @Test
    fun rampIsAStaircase() {
        // Matches the bike log in issue #1: 8 → 12 over 300 s changes level at 38, 113, 188 and 263 s.
        assertEquals(
            listOf(
                LevelRun(0, 38, 8),
                LevelRun(38, 113, 9),
                LevelRun(113, 188, 10),
                LevelRun(188, 263, 11),
                LevelRun(263, 300, 12),
            ),
            Segment(300, 8, endLevel = 12).levelRuns(),
        )
    }

    @Test
    fun downRampStepsDown() {
        val runs = Segment(300, 12, endLevel = 8).levelRuns()
        assertEquals(listOf(12, 11, 10, 9, 8), runs.map { it.level })
    }

    @Test
    fun runsCoverTheSegmentWithNoGaps() {
        for (seg in listOf(Segment(300, 8, endLevel = 12), Segment(61, 1, endLevel = 16), Segment(7, 6, endLevel = 7))) {
            val runs = seg.levelRuns()
            assertEquals(0, runs.first().startSec)
            assertEquals(seg.durationSec, runs.last().endSec)
            runs.zipWithNext().forEach { (a, b) -> assertEquals(a.endSec, b.startSec) }
            // Every second's level matches levelAt.
            runs.forEach { r -> (r.startSec until r.endSec).forEach { t -> assertEquals(seg.levelAt(t), r.level) } }
        }
    }

    @Test
    fun rampToTheSameLevelIsOneRun() {
        assertEquals(listOf(LevelRun(0, 60, 8)), Segment(60, 8, endLevel = 8).levelRuns())
    }
}
