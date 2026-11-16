package app.merachride

import android.app.Application
import app.merachride.data.JsonFileStore
import app.merachride.history.RideCodec
import app.merachride.history.RideRecord
import app.merachride.history.RideTracker
import app.merachride.workout.Workout
import app.merachride.workout.WorkoutCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class MerachRideApp : Application() {
    /** Lives as long as the process, so a ride survives screen rotation and backgrounding. */
    lateinit var session: BikeSession
        private set

    /** Workouts the user made (the built-in ones are in [app.merachride.workout.SampleWorkouts]). */
    lateinit var customWorkouts: JsonFileStore<Workout>
        private set

    /** Saved rides, newest first. */
    lateinit var rideHistory: JsonFileStore<RideRecord>
        private set

    lateinit var rideTracker: RideTracker
        private set

    /** Optional heart rate monitor (chest strap or a watch app that broadcasts heart rate). */
    lateinit var heartRate: HeartRateMonitor
        private set

    /** The MerachRide watch app, which sends heart rate from the watch without Bluetooth sharing. */
    lateinit var watchLink: WatchLink
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        session = BikeSession(this, scope)
        heartRate = HeartRateMonitor(this, scope, session::log)
        customWorkouts = JsonFileStore(
            File(filesDir, "workouts.json"),
            scope,
            WorkoutCodec::encode,
            WorkoutCodec::decode,
        )
        rideHistory = JsonFileStore(
            File(filesDir, "rides.json"),
            scope,
            RideCodec::encode,
            RideCodec::decode,
        )
        rideTracker = RideTracker(scope, session, heartRate, rideHistory)
        watchLink = WatchLink(this, scope, heartRate, session::log)
        followBikeForHeartRate(scope)
    }

    /**
     * Heart rate follows the bike: when the bike connects, start the watch app and look for the saved
     * monitor; when the user disconnects, or the bike stays unreachable for [HR_GIVE_UP_MS], stop whatever
     * was started automatically. A brief reconnect (bike asleep) changes nothing. UNVERIFIED on the phone.
     */
    private fun followBikeForHeartRate(scope: CoroutineScope) {
        scope.launch {
            session.state
                .map { s ->
                    when (s) {
                        is ConnState.Ready -> BikeLink.READY
                        is ConnState.Connecting -> BikeLink.CONNECTING
                        ConnState.Idle -> BikeLink.IDLE
                    }
                }
                .distinctUntilChanged()
                .collectLatest { link ->
                    when (link) {
                        BikeLink.READY -> {
                            heartRate.startAutoConnect()
                            watchLink.onBikeConnected()
                        }
                        BikeLink.IDLE -> {
                            heartRate.stopAutoConnect()
                            watchLink.onBikeGone()
                        }
                        BikeLink.CONNECTING -> {
                            delay(HR_GIVE_UP_MS) // Cancelled if the bike comes back first.
                            heartRate.stopAutoConnect()
                            watchLink.onBikeGone()
                        }
                    }
                }
        }
    }

    private enum class BikeLink { IDLE, CONNECTING, READY }

    private companion object {
        const val HR_GIVE_UP_MS = 5 * 60_000L
    }
}
