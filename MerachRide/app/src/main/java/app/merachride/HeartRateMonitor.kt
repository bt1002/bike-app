package app.merachride

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import app.merachride.ble.BleConnection
import app.merachride.protocol.HeartRateParser
import app.merachride.protocol.HeartRateUuids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Connection to a standard Bluetooth heart rate monitor (Heart Rate Service 0x180D): a chest strap,
 * or a watch app that broadcasts heart rate. Independent of the bike connection.
 *
 * Verified with the user's Pixel Watch 2 (Connected Fitness), 2026-11-15.
 *
 * Readings can also come from the MerachRide watch app via [reportFromWatch] (see [WatchLink]);
 * both sources feed the same [bpm].
 */
@SuppressLint("MissingPermission") // The UI gates everything behind the Bluetooth permissions.
class HeartRateMonitor(
    app: Context,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit,
) {
    sealed interface State {
        data object Idle : State
        data class Connecting(val name: String?, val attempt: Int) : State
        data class Connected(val name: String?) : State
    }

    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = app.getSharedPreferences("heart_rate", Context.MODE_PRIVATE)
    /** True while the background auto-connect is trying, so its failed attempts don't flood the log. */
    @Volatile private var quiet = false
    private val conn = BleConnection(app, HeartRateUuids.SERVICE) { if (!quiet) log("HR: $it") }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _bpm = MutableStateFlow<Int?>(null)
    /** Latest heart rate, or null if there's no recent reading. */
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /**
     * Devices already paired with this phone. A watch paired to the phone (like a Pixel Watch) may not
     * show up in a scan, but can still be connected to directly; connecting checks for the Heart Rate Service.
     */
    fun pairedDevices(): List<SavedDevice> =
        try {
            adapter?.bondedDevices.orEmpty()
                .map { SavedDevice(it.address, it.name) }
                .sortedBy { it.name ?: it.address }
        } catch (e: SecurityException) {
            emptyList()
        }

    @Volatile private var lastReadingAt = 0L
    @Volatile private var lastWatchReadingAt = 0L
    @Volatile private var lastFromWatch = false
    private var sessionJob: Job? = null
    /** True if the current session was started by [startAutoConnect], so it ends when the bike goes away. */
    @Volatile private var sessionIsAuto = false
    private var autoJob: Job? = null

    private val _autoWaiting = MutableStateFlow(false)
    /** True while the app is waiting in the background for the saved monitor to start sharing. */
    val autoWaiting: StateFlow<Boolean> = _autoWaiting.asStateFlow()
    private var scanJob: Job? = null
    private var scanCallback: ScanCallback? = null

    init {
        scope.launch {
            conn.notifications.collect { n ->
                if (n.uuid == HeartRateUuids.MEASUREMENT) {
                    HeartRateParser.parse(n.value)?.let {
                        lastReadingAt = SystemClock.elapsedRealtime()
                        lastFromWatch = false
                        _bpm.value = it
                    }
                }
            }
        }
        // Clear the reading if it stops arriving, so the app never shows (or records) a stale value.
        scope.launch {
            while (isActive) {
                delay(1_000)
                // The watch app may send every few seconds when its screen is off, so give it longer.
                val staleAfter = if (lastFromWatch) WATCH_STALE_MS else STALE_MS
                if (_bpm.value != null && SystemClock.elapsedRealtime() - lastReadingAt > staleAfter) _bpm.value = null
            }
        }
    }

    val savedDevice: SavedDevice?
        get() = prefs.getString(KEY_ADDRESS, null)?.let { SavedDevice(it, prefs.getString(KEY_NAME, null)) }

    /** Whether to connect to [savedDevice] automatically whenever the bike connects. On by default. */
    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO, value).apply()
            if (!value) stopAutoConnect()
        }

    /** A reading from the MerachRide watch app (it doesn't use Bluetooth heart rate sharing). */
    fun reportFromWatch(bpm: Int) {
        if (bpm <= 0) return
        val now = SystemClock.elapsedRealtime()
        lastReadingAt = now
        lastWatchReadingAt = now
        lastFromWatch = true
        _bpm.value = bpm
    }

    private fun watchAppIsSending() = SystemClock.elapsedRealtime() - lastWatchReadingAt < WATCH_STALE_MS

    // ---------------------------------------------------------------- scanning

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || adapter?.isEnabled != true) {
            log("HR: Bluetooth is off")
            return
        }
        stopScan()
        _found.value = emptyList()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onFound(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onFound)
            override fun onScanFailed(errorCode: Int) {
                log("HR: scan failed (code $errorCode)")
                _scanning.value = false
            }
        }
        scanCallback = cb
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, cb)
        _scanning.value = true
        log("HR: scanning for heart rate monitors…")
        scanJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        val cb = scanCallback ?: return
        scanCallback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: Exception) {
            // Bluetooth was turned off mid-scan; nothing to stop.
        }
        _scanning.value = false
    }

    private fun onFound(r: ScanResult) {
        // Only devices advertising the Heart Rate Service (that's how monitors announce themselves).
        val hrs = r.scanRecord?.serviceUuids?.any { it.uuid == HeartRateUuids.SERVICE } == true
        if (!hrs) return
        val name = r.scanRecord?.deviceName ?: r.device.name
        val device = FoundDevice(r.device.address, name, r.rssi, advertisesFtms = false)
        _found.update { list ->
            (list.filterNot { it.address == device.address } + device).sortedByDescending { it.rssi }
        }
    }

    // ---------------------------------------------------------------- connection

    /**
     * Connects to a monitor picked by hand and keeps reconnecting until [disconnect] is called. If it has
     * never answered, it gets [FIRST_CONNECT_TRIES] tries (it may not be a heart rate monitor at all).
     * The saved monitor is otherwise connected by [startAutoConnect] when the bike connects.
     */
    fun connect(address: String, name: String?) {
        stopScan()
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()
        val auto = autoJob
        autoJob = null
        startSession(address, name, alreadyUp = false, auto = false, waitFor = auto)
    }

    /**
     * Called when the bike connects. If there's a saved monitor and [autoReconnect] is on, keeps trying it
     * quietly in the background until it answers (e.g. once Connected Fitness is turned on on the watch),
     * then carries on like [connect]. Does nothing if a monitor is already connected or being connected.
     * UNVERIFIED on the phone.
     */
    fun startAutoConnect() {
        val saved = savedDevice ?: return
        if (!autoReconnect || sessionJob?.isActive == true || autoJob?.isActive == true) return
        autoJob = scope.launch {
            _autoWaiting.value = true
            log("HR: waiting for ${saved.name ?: saved.address} to share heart rate")
            try {
                while (isActive) {
                    // No need to look for Bluetooth sharing while the watch app is already sending.
                    if (!watchAppIsSending()) {
                        quiet = true
                        val up = try {
                            setUp(saved.address)
                        } finally {
                            quiet = false
                        }
                        if (up) {
                            startSession(saved.address, saved.name, alreadyUp = true, auto = true, waitFor = null)
                            return@launch
                        }
                    }
                    delay(AUTO_RETRY_MS)
                }
            } finally {
                _autoWaiting.value = false
            }
        }
    }

    /**
     * Called when the bike goes away. Stops the background search, and disconnects the monitor if the
     * search connected it. A monitor the user connected by hand stays connected.
     */
    fun stopAutoConnect() {
        val auto = autoJob
        autoJob = null
        val endSession = sessionIsAuto && sessionJob?.isActive == true
        if (auto == null && !endSession) return
        scope.launch {
            auto?.cancelAndJoin()
            if (endSession) {
                disconnect()
            } else if (sessionJob?.isActive != true) {
                conn.disconnect() // In case the search was cancelled mid-connect.
            }
        }
    }

    private fun startSession(address: String, name: String?, alreadyUp: Boolean, auto: Boolean, waitFor: Job?) {
        sessionJob?.cancel()
        sessionIsAuto = auto
        sessionJob = scope.launch {
            waitFor?.cancelAndJoin() // Let a background search finish before this session uses the link.
            var attempt = 0
            var everConnected = alreadyUp
            var up = alreadyUp
            while (isActive) {
                if (!up) {
                    attempt++
                    // A device that never worked is probably not a heart rate monitor (e.g. earbuds picked
                    // from the paired list), so stop instead of retrying forever. Once it has worked, keep retrying.
                    if (!everConnected && attempt > FIRST_CONNECT_TRIES) {
                        log("HR: no heart rate from ${name ?: address}. Is heart rate sharing on?")
                        _state.value = State.Idle
                        return@launch
                    }
                    _state.value = State.Connecting(name, attempt)
                    log(if (attempt == 1) "HR: connecting to ${name ?: address}" else "HR: reconnect attempt $attempt")
                    up = setUp(address)
                }
                if (up) {
                    everConnected = true
                    attempt = 0
                    _state.value = State.Connected(name)
                    log("HR: receiving heart rate from ${name ?: address}")
                    conn.connected.first { !it } // Suspend until the link drops.
                    log("HR: connection lost")
                    _state.value = State.Connecting(name, 1)
                    up = false
                }
                delay(1_000L + attempt.coerceAtMost(5) * 2_000L)
            }
        }
    }

    fun disconnect() {
        autoJob?.cancel()
        autoJob = null
        sessionJob?.cancel()
        sessionJob = null
        sessionIsAuto = false
        conn.disconnect()
        _state.value = State.Idle
        _bpm.value = null
        log("HR: disconnected")
    }

    private suspend fun setUp(address: String): Boolean {
        val a = adapter
        if (a == null || !a.isEnabled) {
            if (!quiet) log("HR: Bluetooth is off")
            return false
        }
        if (!conn.connect(a.getRemoteDevice(address))) return false
        if (!conn.enableNotifications(HeartRateUuids.SERVICE, HeartRateUuids.MEASUREMENT, indicate = false)) {
            if (!quiet) log("HR: couldn't subscribe to heart rate")
            conn.disconnect()
            return false
        }
        return true
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val KEY_AUTO = "auto_reconnect"
        const val AUTO_RETRY_MS = 10_000L
        const val WATCH_STALE_MS = 15_000L
        const val SCAN_DURATION_MS = 15_000L
        const val STALE_MS = 5_000L
        const val FIRST_CONNECT_TRIES = 3
    }
}
