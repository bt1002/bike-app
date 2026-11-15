package app.merachride.workout

import app.merachride.protocol.FtmsProtocol

/**
 * One block of a workout. Holds [level] for [durationSec], or, if [endLevel] is set, ramps
 * steadily from [level] at the start to [endLevel] at the end.
 */
data class Segment(
    val durationSec: Int,
    val level: Int,
    val label: String = "",
    val endLevel: Int? = null,
) {
    init {
        require(durationSec > 0) { "durationSec must be > 0" }
    }

    val isRamp: Boolean get() = endLevel != null && endLevel != level

    /** Level to use [offsetSec] seconds into this segment (whole levels, as the bike only takes those). */
    fun levelAt(offsetSec: Int): Int {
        val end = endLevel ?: return level
        val t = offsetSec.coerceIn(0, durationSec)
        // Round the straight line between the two levels. e.g. 8 -> 12 over 300 s changes level at
        // 37.5, 112.5, 187.5 and 262.5 s, so the end levels get half a step each. When an up-ramp
        // is followed by a down-ramp, the peak then lasts as long as every other level.
        return level + Math.round((end - level) * t.toDouble() / durationSec).toInt()
    }

    /** "8" or "8→12". */
    val levelText: String get() = if (isRamp) "$level→$endLevel" else "$level"
}

/** [id] is blank for built-in workouts and a UUID for ones the user made. */
data class Workout(val name: String, val segments: List<Segment>, val id: String = "") {
    init {
        require(segments.isNotEmpty()) { "workout needs at least one segment" }
    }

    val totalSec: Int = segments.sumOf { it.durationSec }

    /** Identifies this workout across rides: the id for custom workouts, the name for built-in ones (blank id). */
    val key: String get() = id.ifBlank { "builtin:$name" }

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

    /** Level the bike should be at [elapsedSec] into the workout. */
    fun levelAt(elapsedSec: Int): Int {
        val i = segmentIndexAt(elapsedSec)
        return segments[i].levelAt(elapsedSec - segmentStart(i))
    }

    /** [levelAt] shifted by the rider's [offset] (the Ride tab's +/−), kept within the bike's 1..16. */
    fun levelAt(elapsedSec: Int, offset: Int): Int =
        (levelAt(elapsedSec) + offset).coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL)
}

/** Snapshot of a workout in progress. */
data class WorkoutState(
    val workout: Workout? = null,
    val elapsedSec: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    /** Levels added to the rest of the workout by the rider (+/− on the Ride tab). */
    val levelOffset: Int = 0,
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
