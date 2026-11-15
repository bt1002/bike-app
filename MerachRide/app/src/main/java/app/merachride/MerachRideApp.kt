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
    }
}
