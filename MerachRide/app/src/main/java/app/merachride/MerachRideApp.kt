package app.merachride

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MerachRideApp : Application() {
    /** Lives as long as the process, so a ride survives screen rotation and backgrounding. */
    lateinit var session: BikeSession
        private set

    override fun onCreate() {
        super.onCreate()
        session = BikeSession(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
