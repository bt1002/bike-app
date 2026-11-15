package app.merachride.workout

/** One block of a workout: hold [level] for [durationSec]. */
data class Segment(val durationSec: Int, val level: Int, val label: String = "") {
    init {
        require(durationSec > 0) { "durationSec must be > 0" }
    }
}

data class Workout(val name: String, val segments: List<Segment>) {
    init {
        require(segments.isNotEmpty()) { "workout needs at least one segment" }
    }

    val totalSec: Int = segments.sumOf { it.durationSec }

    /** Index of the segment active at [elapsedSec]. Past the end, returns the last segment. */
    fun segmentIndexAt(elapsedSec: Int): Int {
        var end = 0
        segments.forEachIndexed { i, s ->
            end += s.durationSec
            if (elapsedSec < end) return i
        }
        return segments.lastIndex
    }

    /** Seconds from workout start to the start of segment [index]. */
    fun segmentStart(index: Int): Int = segments.take(index).sumOf { it.durationSec }
}

/** Snapshot of a workout in progress. */
data class WorkoutState(
    val workout: Workout? = null,
    val elapsedSec: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
) {
    val segmentIndex: Int
        get() = workout?.segmentIndexAt(elapsedSec) ?: 0

    val currentSegment: Segment?
        get() = workout?.segments?.getOrNull(segmentIndex)

    val nextSegment: Segment?
        get() = if (finished) null else workout?.segments?.getOrNull(segmentIndex + 1)

    val segmentRemainingSec: Int
        get() {
            val w = workout ?: return 0
            val end = w.segmentStart(segmentIndex) + w.segments[segmentIndex].durationSec
            return (end - elapsedSec).coerceAtLeast(0)
        }

    val totalRemainingSec: Int
        get() = ((workout?.totalSec ?: 0) - elapsedSec).coerceAtLeast(0)

    val progress: Float
        get() = workout?.let { elapsedSec.toFloat() / it.totalSec } ?: 0f
}
