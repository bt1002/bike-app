package app.merachride.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCodecTest {

    @Test
    fun roundTripsWorkouts() {
        val list = listOf(
            Workout("Tempo", listOf(Segment(300, 4, "Warm-up"), Segment(600, 9)), id = "a"),
            Workout("Sprints \"x\"", listOf(Segment(20, 16, "Go")), id = "b"),
        )
        assertEquals(list, WorkoutCodec.decode(WorkoutCodec.encode(list)))
    }

    @Test
    fun skipsDamagedEntriesButKeepsTheRest() {
        val text = """
            {"version":1,"workouts":[
              {"id":"bad","name":"No segments","segments":[]},
              {"id":"ok","name":"Fine","segments":[{"sec":60,"level":20}]}
            ]}
        """.trimIndent()
        val result = WorkoutCodec.decode(text)
        assertEquals(1, result.size)
        assertEquals("ok", result[0].id)
        assertEquals(16, result[0].segments[0].level) // Clamped to the bike's range.
    }
}
