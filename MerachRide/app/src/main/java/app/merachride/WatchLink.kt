package app.merachride

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.merachride.protocol.WatchProtocol
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Link to the MerachRide watch app (`wear/` module), which reads heart rate on the watch and sends it
 * here over the Wear OS Data Layer. No Bluetooth heart rate sharing (Connected Fitness) needed.
 *
 * The phone opens the watch app when the bike connects (if [autoStart] is on), pings it every
 * [WatchProtocol.PING_INTERVAL_MS] so it keeps going, and tells it to stop when the bike goes away.
 * If the watch app is started by hand, it's adopted as soon as its first reading arrives.
 *
 * UNVERIFIED: not yet built or tried on the phone and watch.
 */
class WatchLink(
    app: Context,
    private val scope: CoroutineScope,
    private val heartRate: HeartRateMonitor,
    private val log: (String) -> Unit,
) {
    sealed interface State {
        /** Not in use. */
        data object Off : State
        /** Looking for the watch and opening the app on it. */
        data object Opening : State
        /** The watch app is open but no reading has arrived recently. */
        data class Waiting(val watchName: String?) : State
        data class Receiving(val watchName: String?) : State
        /** No connected watch has the MerachRide watch app installed. */
        data object NotInstalled : State
        data class Failed(val reason: String) : State
    }

    private val prefs = app.getSharedPreferences("watch_app", Context.MODE_PRIVATE)
    private val messages = Wearable.getMessageClient(app)
    private val capabilities = Wearable.getCapabilityClient(app)
    private val nodes = Wearable.getNodeClient(app)
    private val remote = RemoteActivityHelper(app)

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Whether to open the watch app automatically when the bike connects. On by default. */
    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    @Volatile private var watchNodeId: String? = null
    @Volatile private var watchName: String? = null
    @Volatile private var lastBpmAt = 0L
    /** True if the bike connection started the watch app, so the bike going away stops it. */
    @Volatile private var startedByBike = false
    private var job: Job? = null

    val active: Boolean get() = job?.isActive == true

    init {
        try {
            messages.addListener(::onMessage)
        } catch (e: Exception) {
            log("Watch: Wear OS messaging unavailable (${e.message})")
        }
    }

    /** Called when the bike connects. Opens the watch app if [autoStart] is on and it isn't running. */
    fun onBikeConnected() {
        if (autoStart && !active) start(byBike = true)
    }

    /** Called when the bike has gone away. Stops the watch app only if the bike connection started it. */
    fun onBikeGone() {
        if (active && startedByBike) stop()
    }

    /** Opens the watch app and keeps it going until [stop]. */
    fun start(byBike: Boolean = false) {
        if (active) return
        startedByBike = byBike
        _state.value = State.Opening
        job = scope.launch {
            val node = try {
                findWatch()
            } catch (e: Exception) {
                fail("couldn't reach the watch (${e.message})")
                return@launch
            }
            if (node == null) {
                log("Watch: no connected watch has the MerachRide watch app")
                _state.value = State.NotInstalled
                return@launch
            }
            watchNodeId = node.id
            watchName = node.displayName
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(WatchProtocol.START_URI))
            try {
                remote.startRemoteActivity(intent, node.id).awaitFuture()
            } catch (e: Exception) {
                fail("couldn't open the app on ${node.displayName} (${e.message})")
                return@launch
            }
            log("Watch: opened MerachRide on ${node.displayName}")
            keepAlive()
        }
    }

    /** Tells the watch app to stop and stops pinging it. */
    fun stop() {
        job?.cancel()
        job = null
        startedByBike = false
        _state.value = State.Off
        val id = watchNodeId ?: return
        scope.launch {
            try {
                messages.sendMessage(id, WatchProtocol.PATH_STOP, null).awaitTask()
                log("Watch: asked the watch app to stop")
            } catch (e: Exception) {
                log("Watch: couldn't reach the watch to stop it (${e.message})")
            }
        }
    }

    /** Pings the watch and keeps [state] current, until cancelled. */
    private suspend fun keepAlive() {
        var sincePing = WatchProtocol.PING_INTERVAL_MS // Ping straight away.
        while (currentCoroutineContext().isActive) {
            if (sincePing >= WatchProtocol.PING_INTERVAL_MS) {
                sincePing = 0
                watchNodeId?.let { id ->
                    try {
                        messages.sendMessage(id, WatchProtocol.PATH_PING, null).awaitTask()
                    } catch (e: Exception) {
                        // The watch is out of range or asleep; keep trying. It stops itself if this goes on.
                    }
                }
            }
            val fresh = SystemClock.elapsedRealtime() - lastBpmAt < FRESH_MS
            _state.value = if (fresh) State.Receiving(watchName) else State.Waiting(watchName)
            delay(TICK_MS)
            sincePing += TICK_MS
        }
    }

    private fun onMessage(e: MessageEvent) {
        when (e.path) {
            WatchProtocol.PATH_BPM -> {
                val bpm = WatchProtocol.decodeBpm(e.data) ?: return
                lastBpmAt = SystemClock.elapsedRealtime()
                heartRate.reportFromWatch(bpm)
                if (!active) {
                    // Started by hand on the watch: adopt it so it gets pinged.
                    watchNodeId = e.sourceNodeId
                    startedByBike = false
                    log("Watch: receiving heart rate from the watch app")
                    job = scope.launch {
                        watchName = try {
                            nodes.connectedNodes.awaitTask().firstOrNull { it.id == e.sourceNodeId }?.displayName
                        } catch (ex: Exception) {
                            null
                        }
                        keepAlive()
                    }
                }
                _state.value = State.Receiving(watchName)
            }
            WatchProtocol.PATH_STOPPED -> {
                job?.cancel()
                job = null
                startedByBike = false
                _state.value = State.Off
                log("Watch: the watch app stopped")
            }
        }
    }

    /** The nearest reachable watch with the MerachRide watch app, or null. */
    private suspend fun findWatch(): Node? {
        val info = capabilities.getCapability(WatchProtocol.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).awaitTask()
        return info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
    }

    private fun fail(reason: String) {
        log("Watch: $reason")
        _state.value = State.Failed(reason)
    }

    private companion object {
        const val KEY_AUTO = "auto_start"
        const val TICK_MS = 5_000L
        const val FRESH_MS = 15_000L
    }
}

/** Awaits a Play services [Task] without the kotlinx-coroutines-play-services dependency. */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t ->
        val e = t.exception
        when {
            e != null -> c.resumeWithException(e)
            t.isCanceled -> c.cancel()
            else -> c.resume(t.result)
        }
    }
}

/** Awaits a [ListenableFuture] without the kotlinx-coroutines-guava dependency. */
internal suspend fun <T> ListenableFuture<T>.awaitFuture(): T = suspendCancellableCoroutine { c ->
    addListener({
        try {
            c.resume(get())
        } catch (e: ExecutionException) {
            c.resumeWithException(e.cause ?: e)
        } catch (e: Exception) {
            c.resumeWithException(e)
        }
    }, Runnable::run)
    c.invokeOnCancellation { cancel(false) }
}
