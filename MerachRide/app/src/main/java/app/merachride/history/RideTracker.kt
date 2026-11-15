package app.merachride.history

import android.os.SystemClock
import app.merachride.BikeSession
import app.merachride.ConnState
import app.merachride.HeartRateMonitor
import app.merachride.data.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** What the Ride screen shows about the ride in progress. */
data class RideProgress(val elapsedSec: Int, val distanceM: Int, val energyKcal: Int)

/**
 * Turns bike data into saved rides.
 *
 * A ride starts the first time the rider pedals while connected. It ends when the user taps
 * Finish, disconnects, or the bike stays disconnected for [MAX_GAP_MS]. Rides shorter than
 * [MIN_SAVED_SEC] aren't saved. Everything here runs on the main thread, so no locking is needed.
 */
class RideTracker(
    scope: CoroutineScope,
    private val session: BikeSession,
    private val heartRate: HeartRateMonitor,
    private val history: JsonFileStore<RideRecord>,
) {
    private val _current = MutableStateFlow<RideProgress?>(null)
    /** The ride in progress, or null if none. */
    val current: StateFlow<RideProgress?> = _current.asStateFlow()

    private var recorder: RideRecorder? = null
    private var disconnectedSince: Long? = null

    init {
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1_000)
                tick()
            }
        }
    }

    private fun tick() {
        val state = session.state.value
        // A separate heart rate monitor wins over the bike's own reading (the S29 reports none without a strap).
        val data = session.data.value.let { d -> heartRate.bpm.value?.let { d.copy(heartRateBpm = it) } ?: d }
        val r = recorder

        when {
            state is ConnState.Ready -> {
                disconnectedSince = null
                if (r == null) {
                    if ((data.cadenceRpm ?: 0.0) > 0.0) {
                        recorder = RideRecorder(UUID.randomUUID().toString(), System.currentTimeMillis())
                        session.log("Ride started")
                    }
                } else {
                    r.tick(data)
                    val ws = session.workout.state.value
                    if (ws.running) ws.workout?.name?.let { r.workoutName = it }
                }
            }
            r == null -> Unit
            state is ConnState.Idle -> finish() // The user disconnected.
            else -> {
                val now = SystemClock.elapsedRealtime()
                val since = disconnectedSince ?: now.also { disconnectedSince = it }
                if (now - since >= MAX_GAP_MS) {
                    session.log("Bike gone for ${MAX_GAP_MS / 60_000} minutes; ending the ride")
                    finish()
                }
            }
        }
        _current.value = recorder?.let { RideProgress(it.elapsedSec, it.distanceM, it.energyKcal) }
    }

    /** Ends the ride in progress and saves it if it's long enough. Call on the main thread. */
    fun finish() {
        val r = recorder ?: return
        recorder = null
        disconnectedSince = null
        _current.value = null
        val record = r.record()
        if (record.durationSec >= MIN_SAVED_SEC) {
            history.update { listOf(record) + it }
            session.log("Ride saved (${record.durationSec / 60} min)")
        } else {
            session.log("Ride under a minute; not saved")
        }
    }

    private companion object {
        const val MAX_GAP_MS = 3 * 60_000L
        const val MIN_SAVED_SEC = 60
    }
}
