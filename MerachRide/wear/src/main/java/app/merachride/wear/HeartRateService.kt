package app.merachride.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Runs a Health Services exercise (stationary biking, heart rate only) and sends each reading to the
 * phone app. A foreground service, so it keeps going with the screen off.
 *
 * Stops when: Stop is tapped on the watch, the phone sends [WatchProtocol.PATH_STOP], no ping has come
 * from the phone for [WatchProtocol.WATCH_TIMEOUT_MS], after [MAX_RUN_MS], or the exercise is ended
 * elsewhere. So the sensor never runs for hours by mistake.
 *
 * UNVERIFIED: not yet built or run on the watch.
 */
class HeartRateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var exercise: ExerciseClient
    private lateinit var messages: MessageClient

    private var phoneNodeId: String? = null
    private var lastPingAt = 0L
    private var startedAt = 0L
    private var running = false
    private var stopping = false

    private val messageListener = MessageClient.OnMessageReceivedListener { e ->
        when (e.path) {
            WatchProtocol.PATH_PING -> {
                lastPingAt = SystemClock.elapsedRealtime()
                phoneNodeId = e.sourceNodeId
            }
            WatchProtocol.PATH_STOP -> stopSharing("Stopped from the phone", notifyPhone = false)
        }
    }

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val bpm = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.roundToInt()
            if (bpm != null && bpm > 0) onBpm(bpm)
            if (update.exerciseStateInfo.state.isEnded && running && !stopping) {
                stopSharing("The workout was ended on the watch", notifyPhone = true)
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "Exercise callback registration failed", throwable)
            fail("Couldn't get heart rate updates (${throwable.message})")
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            if (dataType == DataType.HEART_RATE_BPM && availability == DataTypeAvailability.ACQUIRING) {
                HrShare.message.value = "Finding your pulse…"
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        exercise = HealthServices.getClient(this).exerciseClient
        messages = Wearable.getMessageClient(this)
        messages.addListener(messageListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing("Stopped", notifyPhone = true)
            return START_NOT_STICKY
        }
        if (stopping) return START_NOT_STICKY // Tapped Start during a stop; tap again once it's stopped.
        if (!goForeground()) return START_NOT_STICKY
        val force = intent?.getBooleanExtra(EXTRA_FORCE, false) == true
        if (!running || force) {
            running = true
            scope.launch { begin(force) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        messages.removeListener(messageListener)
        if (running && !stopping) {
            // Killed without a proper stop: don't leave the heart rate sensor running in Health Services.
            exercise.endExerciseAsync()
            HrShare.phase.value = HrShare.Phase.STOPPED
            HrShare.bpm.value = null
        }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun begin(force: Boolean) {
        HrShare.phase.value = HrShare.Phase.STARTING
        HrShare.message.value = "Starting heart rate…"
        startedAt = SystemClock.elapsedRealtime()
        lastPingAt = startedAt // Give the phone one timeout's grace for its first ping.
        launchPhoneLookup()
        try {
            val caps = exercise.getCapabilitiesAsync().awaitFuture()
            val type = if (ExerciseType.BIKING_STATIONARY in caps.supportedExerciseTypes) {
                ExerciseType.BIKING_STATIONARY
            } else {
                ExerciseType.BIKING
            }
            if (DataType.HEART_RATE_BPM !in caps.getExerciseTypeCapabilities(type).supportedDataTypes) {
                fail("This watch can't measure heart rate during a workout")
                return
            }
            val info = exercise.getCurrentExerciseInfoAsync().awaitFuture()
            when (info.exerciseTrackedStatus) {
                ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> {
                    // Already ours (the app was reopened): just listen again.
                    exercise.setUpdateCallback(callback)
                }
                ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> if (!force) {
                    // Starting ours would end the other app's workout, so ask first (Start anyway).
                    running = false
                    HrShare.phase.value = HrShare.Phase.OTHER_WORKOUT
                    HrShare.message.value = "Another workout is running on the watch. Start anyway ends it."
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                } else {
                    startExercise(type, caps.supportedBatchingModeOverrides)
                }
                else -> startExercise(type, caps.supportedBatchingModeOverrides)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the exercise", e)
            fail("Couldn't start heart rate (${e.message})")
            return
        }
        watchdog()
    }

    private suspend fun startExercise(type: ExerciseType, batchingModes: Set<BatchingMode>) {
        exercise.setUpdateCallback(callback)
        // With the screen off, Health Services batches readings; this keeps heart rate coming every ~5 s.
        val batching = setOf(BatchingMode.HEART_RATE_5_SECONDS).intersect(batchingModes)
        val config = ExerciseConfig.builder(type)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
            .setBatchingModeOverrides(batching)
            .build()
        exercise.startExerciseAsync(config).awaitFuture()
        Log.i(TAG, "Exercise started ($type, batching $batching)")
    }

    /** Stops if the phone has gone quiet, or after [MAX_RUN_MS]. Returns when stopping. */
    private suspend fun watchdog() {
        while (!stopping) {
            delay(10_000)
            val now = SystemClock.elapsedRealtime()
            when {
                now - lastPingAt > WatchProtocol.WATCH_TIMEOUT_MS ->
                    stopSharing("No word from the phone app for 3 minutes", notifyPhone = true)
                now - startedAt > MAX_RUN_MS ->
                    stopSharing("Stopped after 4 hours", notifyPhone = true)
            }
        }
    }

    private fun onBpm(bpm: Int) {
        HrShare.bpm.value = bpm
        HrShare.phase.value = HrShare.Phase.MEASURING
        HrShare.message.value = if (phoneNodeId == null) "Looking for the phone…" else null
        val node = phoneNodeId ?: return
        messages.sendMessage(node, WatchProtocol.PATH_BPM, WatchProtocol.encodeBpm(bpm))
            .addOnFailureListener { Log.w(TAG, "Couldn't send heart rate", it) }
    }

    /** Finds the phone running MerachRide (pings also tell us, but they come only every 30 s). */
    private fun launchPhoneLookup() {
        scope.launch {
            try {
                val info = Wearable.getCapabilityClient(this@HeartRateService)
                    .getCapability(WatchProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .awaitTask()
                val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
                if (node != null && phoneNodeId == null) phoneNodeId = node.id
            } catch (e: Exception) {
                Log.w(TAG, "Phone lookup failed", e)
            }
        }
    }

    private fun fail(reason: String) {
        if (stopping) return
        stopping = true
        running = false
        HrShare.phase.value = HrShare.Phase.ERROR
        HrShare.bpm.value = null
        HrShare.message.value = reason
        scope.launch { shutDown(notifyPhone = true) }
    }

    private fun stopSharing(reason: String, notifyPhone: Boolean) {
        if (stopping) return
        stopping = true
        running = false
        HrShare.phase.value = HrShare.Phase.STOPPED
        HrShare.bpm.value = null
        HrShare.message.value = reason
        scope.launch { shutDown(notifyPhone) }
    }

    /** Ends our exercise (if one is running), tells the phone, and stops the service. */
    private suspend fun shutDown(notifyPhone: Boolean) {
        try {
            exercise.clearUpdateCallbackAsync(callback).awaitFuture()
        } catch (e: Exception) {
            // Wasn't registered.
        }
        try {
            // Checked rather than remembered, so Stop also works if this service was restarted.
            val info = exercise.getCurrentExerciseInfoAsync().awaitFuture()
            if (info.exerciseTrackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                exercise.endExerciseAsync().awaitFuture()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't end the exercise", e)
        }
        val node = phoneNodeId
        if (notifyPhone && node != null) {
            try {
                messages.sendMessage(node, WatchProtocol.PATH_STOPPED, null).awaitTask()
            } catch (e: Exception) {
                // The phone is out of reach; it gives up on its own.
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Heart rate sharing", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Sending heart rate to the phone")
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        return try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            // E.g. the body sensors permission was revoked.
            Log.w(TAG, "startForeground failed", e)
            running = false
            HrShare.phase.value = HrShare.Phase.ERROR
            HrShare.message.value = "Couldn't start (${e.message})"
            stopSelf()
            false
        }
    }

    companion object {
        const val ACTION_STOP = "app.merachride.wear.STOP"
        const val EXTRA_FORCE = "force"
        private const val TAG = "MerachRideWatch"
        private const val CHANNEL = "heart_rate"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RUN_MS = 4 * 60 * 60_000L
    }
}
