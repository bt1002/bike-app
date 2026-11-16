package app.merachride.history

import app.merachride.workout.Workout

/** Averages from the last few rides of one workout, used as the pace to beat. */
data class PaceTarget(
    val avgSpeedKmh: Double?,
    val avgHeartRateBpm: Int?,
    /** How many past rides the averages come from (1..[PaceTarget.RIDES]). */
    val rideCount: Int,
) {
    /** Where the current value sits against the target. */
    enum class Trend { AHEAD, ON_PACE, BEHIND }

    companion object {
        /** Number of most recent rides of the same workout to average. */
        const val RIDES = 5

        /** Within ±2% of the target counts as on pace, so the arrow doesn't flicker. */
        const val TOLERANCE = 0.02

        /**
         * Target for [workout] from [history] (newest first, as stored). Uses the [RIDES] most recent
         * rides of the same workout that recorded a speed or heart rate. Null if there are none yet.
         * Rides saved before workout keys existed are matched by name.
         */
        fun forWorkout(history: List<RideRecord>, workout: Workout): PaceTarget? {
            val rides = history
                .sortedByDescending { it.startEpochMs }
                .filter { r -> if (r.workoutKey != null) r.workoutKey == workout.key else r.workoutName == workout.name }
                .filter { it.avgSpeedKmh != null || it.avgHeartRateBpm != null }
                .take(RIDES)
            if (rides.isEmpty()) return null
            val speeds = rides.mapNotNull { it.avgSpeedKmh }
            val hrs = rides.mapNotNull { it.avgHeartRateBpm }
            return PaceTarget(
                avgSpeedKmh = speeds.takeIf { it.isNotEmpty() }?.average(),
                avgHeartRateBpm = hrs.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
                rideCount = rides.size,
            )
        }

        /** Compares [current] with [target]. Null if either is unknown or the target is 0. */
        fun trend(current: Double?, target: Double?): Trend? {
            if (current == null || target == null || target <= 0.0) return null
            val diff = (current - target) / target
            return when {
                diff > TOLERANCE -> Trend.AHEAD
                diff < -TOLERANCE -> Trend.BEHIND
                else -> Trend.ON_PACE
            }
        }
    }
}
