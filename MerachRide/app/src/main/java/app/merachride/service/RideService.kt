package app.merachride.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.merachride.ConnState
import app.merachride.MainActivity
import app.merachride.MerachRideApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground service that keeps the process (and so the Bluetooth link and workout timer)
 * alive while a bike is connected. The connection itself lives in [app.merachride.BikeSession].
 */
class RideService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ride", NotificationManager.IMPORTANCE_LOW),
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build("Connecting to bike…"), type)

        val session = (application as MerachRideApp).session
        scope.launch {
            combine(session.state, session.data, session.targetLevel) { state, data, level ->
                when (state) {
                    ConnState.Idle -> "Not connected"
                    is ConnState.Connecting -> "Connecting to bike…"
                    is ConnState.Ready -> buildString {
                        append("Level ${level ?: data.resistanceLevel ?: "–"}")
                        append(" · ${data.powerW ?: "–"} W")
                        append(" · ${data.cadenceRpm?.roundToInt() ?: "–"} rpm")
                    }
                }
            }
                .distinctUntilChanged()
                .conflate()
                .collect { text ->
                    nm.notify(NOTIFICATION_ID, build(text))
                    delay(2_000) // Android throttles frequent notification updates anyway.
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Merach Ride")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "ride"
        const val NOTIFICATION_ID = 1
    }
}
