package app.merachride.workout

/** Built-in workouts. Levels are the bike's 1–16 resistance scale. */
object SampleWorkouts {
    private fun mins(m: Int) = m * 60

    val quickTest = Workout(
        "Quick Test",
        listOf(
            Segment(30, 2, "Easy"),
            Segment(30, 8, "Medium"),
            Segment(30, 16, "Max"),
            Segment(30, 1, "Recover"),
        ),
    )

    val easySpin = Workout(
        "Easy Spin",
        listOf(
            Segment(mins(5), 3, "Warm-up"),
            Segment(mins(10), 5, "Steady"),
            Segment(mins(5), 3, "Cool-down"),
        ),
    )

    val intervals = Workout(
        "1:1 Intervals",
        buildList {
            add(Segment(mins(5), 4, "Warm-up"))
            repeat(7) { i ->
                add(Segment(60, 12, "Hard ${i + 1}/7"))
                add(Segment(60, 5, "Easy"))
            }
            add(Segment(mins(5), 3, "Cool-down"))
        },
    )

    val hillPyramid = Workout(
        "Hill Pyramid",
        buildList {
            add(Segment(mins(4), 4, "Warm-up"))
            listOf(6, 8, 10, 12, 14, 12, 10, 8, 6).forEach { level ->
                add(Segment(mins(2), level, "Climb"))
            }
            add(Segment(mins(4), 3, "Cool-down"))
        },
    )

    /** 30 minutes alternating level 8 and level 12 every 5 minutes. */
    val steps8to12 = Workout(
        "8 / 12 Steps",
        buildList {
            repeat(3) { i ->
                add(Segment(mins(5), 8, "Steady ${i + 1}/3"))
                add(Segment(mins(5), 12, "Push ${i + 1}/3"))
            }
        },
    )

    val all: List<Workout> = listOf(quickTest, easySpin, intervals, hillPyramid, steps8to12)
}
