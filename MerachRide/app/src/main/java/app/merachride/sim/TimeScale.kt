package app.merachride.sim

/**
 * How fast "one second" passes for the workout timer, ride recording and the simulated bike.
 * Always 1 with the real bike; the simulator's debug panel can raise it so long workouts can be
 * checked quickly. Rides recorded at a higher factor count simulated seconds, not real ones.
 */
object TimeScale {
    @Volatile var factor: Int = 1
        set(value) {
            field = value.coerceIn(1, MAX)
        }

    /** Real milliseconds per simulated second. */
    val tickMs: Long get() = 1_000L / factor

    const val MAX = 60
}
