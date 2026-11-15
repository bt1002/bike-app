package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutImportTest {

    private val a = Workout("A", listOf(Segment(60, 5)), id = "a")
    private val b = Workout("B", listOf(Segment(120, 8, endLevel = 12)), id = "b")

    @Test
    fun addsNewAndReplacesSameId() {
        val changedA = a.copy(name = "A v2")
        val r = WorkoutImport.merge(listOf(a), WorkoutCodec.encode(listOf(changedA, b))) { "new" }
        assertEquals(listOf(changedA, b), r.workouts)
        assertEquals(1, r.added)
        assertEquals(1, r.updated)
        assertEquals(0, r.skipped)
        assertEquals("Imported: added 1, updated 1.", r.summary)
    }

    @Test
    fun readsTheWebBuildersPrettyPrintedFile() {
        // Same shape as JSON.stringify(..., null, 2) in tools/workout-builder.html.
        val text = """
            {
              "version": 1,
              "workouts": [
                {
                  "id": "3f1c",
                  "name": "Ramps",
                  "segments": [
                    { "sec": 300, "level": 8, "label": "Up", "endLevel": 12 },
                    { "sec": 300, "level": 12, "label": "", "endLevel": null }
                  ]
                }
              ]
            }
        """.trimIndent()
        val r = WorkoutImport.merge(emptyList(), text) { "new" }
        assertEquals(
            listOf(Workout("Ramps", listOf(Segment(300, 8, "Up", endLevel = 12), Segment(300, 12)), id = "3f1c")),
            r.workouts,
        )
    }

    @Test
    fun givesIdlessWorkoutsANewId() {
        val text = """{"workouts":[{"name":"X","segments":[{"sec":60,"level":3}]}]}"""
        val r = WorkoutImport.merge(emptyList(), text) { "generated" }
        assertEquals("generated", r.workouts.single().id)
    }

    @Test
    fun countsSkippedEntries() {
        val text = """{"workouts":[{"id":"x","name":"Bad","segments":[]},{"id":"y","name":"Ok","segments":[{"sec":30,"level":2}]}]}"""
        val r = WorkoutImport.merge(emptyList(), text) { "n" }
        assertEquals(1, r.added)
        assertEquals(1, r.skipped)
        assertEquals("Imported: added 1, skipped 1 damaged.", r.summary)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFilesWithoutAWorkoutsList() {
        WorkoutImport.merge(emptyList(), """{"rides":[]}""") { "n" }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonJson() {
        WorkoutImport.merge(emptyList(), "not json") { "n" }
    }
}
