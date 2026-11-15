package app.merachride.workout

import app.merachride.protocol.FtmsProtocol

/** One segment as typed into the editor. Durations stay as text until the workout is saved. */
data class SegmentDraft(
    val minutes: String = "1",
    val seconds: String = "0",
    val level: Int = 5,
    val label: String = "",
    /** Null = hold [level]; otherwise ramp from [level] to this. */
    val endLevel: Int? = null,
) {
    /** Duration in seconds, or null if the fields don't make a valid duration. */
    val durationSec: Int?
        get() {
            val m = minutes.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
            val s = seconds.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
            if (m !in 0..MAX_MINUTES || s !in 0..59) return null
            return (m * 60 + s).takeIf { it > 0 }
        }

    companion object {
        const val MAX_MINUTES = 600

        fun from(s: Segment) = SegmentDraft(
            minutes = (s.durationSec / 60).toString(),
            seconds = (s.durationSec % 60).toString(),
            level = s.level,
            label = s.label,
            endLevel = s.endLevel,
        )
    }
}

/** The workout being edited. [toWorkout] gives the result, or null while something is invalid. */
data class WorkoutDraft(
    val id: String,
    val name: String = "",
    val segments: List<SegmentDraft> = listOf(SegmentDraft()),
) {
    /** Why the draft can't be saved yet, or null if it can. */
    val problem: String?
        get() = when {
            name.isBlank() -> "Give the workout a name"
            segments.isEmpty() -> "Add at least one segment"
            segments.any { it.durationSec == null } -> "Each segment needs a time (minutes, and seconds 0–59)"
            else -> null
        }

    fun toWorkout(): Workout? {
        if (problem != null) return null
        return Workout(
            name = name.trim(),
            segments = segments.map {
                Segment(
                    durationSec = it.durationSec!!,
                    level = it.level.coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL),
                    label = it.label.trim(),
                    endLevel = it.endLevel?.coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL),
                )
            },
            id = id,
        )
    }

    fun update(index: Int, change: (SegmentDraft) -> SegmentDraft) =
        copy(segments = segments.mapIndexed { i, s -> if (i == index) change(s) else s })

    fun remove(index: Int) = copy(segments = segments.filterIndexed { i, _ -> i != index })

    /** Moves segment [index] by [delta] (−1 up, +1 down). Out-of-range moves do nothing. */
    fun move(index: Int, delta: Int): WorkoutDraft {
        val to = index + delta
        if (index !in segments.indices || to !in segments.indices) return this
        val list = segments.toMutableList()
        list.add(to, list.removeAt(index))
        return copy(segments = list)
    }

    /**
     * Adds a segment after the last one, copying its time so building intervals is quick.
     * After a ramp, the new segment ramps back the other way; otherwise it holds the same level.
     */
    fun addSegment(): WorkoutDraft {
        val last = segments.lastOrNull() ?: return copy(segments = listOf(SegmentDraft()))
        val end = last.endLevel
        val next = if (end != null) last.copy(level = end, endLevel = last.level, label = "") else last.copy(label = "")
        return copy(segments = segments + next)
    }

    companion object {
        fun from(w: Workout, id: String = w.id, name: String = w.name) =
            WorkoutDraft(id = id, name = name, segments = w.segments.map(SegmentDraft::from))
    }
}
