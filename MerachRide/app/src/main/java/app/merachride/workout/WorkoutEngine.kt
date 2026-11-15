package app.merachride.workout

import app.merachride.protocol.BikeController
import app.merachride.sim.TimeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs a [Workout]: a 1-second ticker advances time, and whenever the workout's level changes
 * (a new segment, or the next step of a ramp) it sends that level to the bike.
 *
 * All methods must be called on the main thread; the ticker also runs there, so state
 * changes never race.
 */
class WorkoutEngine(
    private val scope: CoroutineScope,
    private val bike: BikeController,
) {
    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** Level the engine last sent, so a ramp only sends a command when the level actually changes. */
    private var lastSent: Int? = null

    fun load(workout: Workout) {
        stopTicker()
        _state.value = WorkoutState(workout = workout)
    }

    fun unload() {
        stopTicker()
        _state.value = WorkoutState()
    }

    fun start() {
        val current = _state.value
        val workout = current.workout ?: return
        if (current.running) return
        val s = if (current.finished) WorkoutState(workout = workout) else current
        _state.value = s.copy(running = true)
        lastSent = null // Always send on start/resume, in case the level changed while paused.
        applyLevel(workout.levelAt(s.elapsedSec, s.levelOffset))

        ticker = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(TimeScale.tickMs) // 1 s, unless the simulator speeds time up.
                val before = _state.value
                val w = before.workout ?: break
                val t = before.elapsedSec + 1
                if (t >= w.totalSec) {
                    _state.value = before.copy(elapsedSec = w.totalSec, running = false, finished = true)
                    break
                }
                _state.value = before.copy(elapsedSec = t)
                applyLevel(w.levelAt(t, before.levelOffset))
            }
        }
    }

    fun pause() {
        stopTicker()
        _state.value = _state.value.copy(running = false)
    }

    /** Jumps to the start of the next segment (or finishes if this was the last one). */
    fun skip() {
        val s = _state.value
        val w = s.workout ?: return
        if (s.finished) return
        val next = s.segmentIndex + 1
        if (next > w.segments.lastIndex) {
            stopTicker()
            _state.value = s.copy(elapsedSec = w.totalSec, running = false, finished = true)
        } else {
            _state.value = s.copy(elapsedSec = w.segmentStart(next))
            if (s.running) applyLevel(w.levelAt(w.segmentStart(next), s.levelOffset))
        }
    }

    /**
     * Makes the rest of the workout [delta] levels harder (+1) or easier (−1). Takes effect at once
     * if running, otherwise on resume. Levels stay within 1..16 however far the offset goes.
     */
    fun bumpLevels(delta: Int) {
        val s = _state.value
        val w = s.workout ?: return
        if (s.finished) return
        val offset = (s.levelOffset + delta).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        _state.value = s.copy(levelOffset = offset)
        if (s.running) applyLevel(w.levelAt(s.elapsedSec, offset))
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        /** Enough to take any level to either end of the 1..16 range. */
        const val MAX_OFFSET = 15
    }

    private fun applyLevel(level: Int) {
        if (level == lastSent) return
        lastSent = level
        scope.launch { bike.setResistanceLevel(level) }
    }
}
