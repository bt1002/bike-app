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
 * UNVERIFIED with the user's Pixel Watch: it depends on a watch app that broadcasts the standard service.
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
    private val conn = BleConnection(app, HeartRateUuids.SERVICE) { log("HR: $it") }

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
    private var sessionJob: Job? = null
    private var scanJob: Job? = null
    private var scanCallback: ScanCallback? = null

    init {
        scope.launch {
            conn.notifications.collect { n ->
                if (n.uuid == HeartRateUuids.MEASUREMENT) {
                    HeartRateParser.parse(n.value)?.let {
                        lastReadingAt = SystemClock.elapsedRealtime()
                        _bpm.value = it
                    }
                }
            }
        }
        // Clear the reading if it stops arriving, so the app never shows (or records) a stale value.
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (_bpm.value != null && SystemClock.elapsedRealtime() - lastReadingAt > STALE_MS) _bpm.value = null
            }
        }
    }

    val savedDevice: SavedDevice?
        get() = prefs.getString(KEY_ADDRESS, null)?.let { SavedDevice(it, prefs.getString(KEY_NAME, null)) }

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
     * Connects and keeps reconnecting until [disconnect] is called.
     *
     * If the monitor has never answered, a device picked by hand is given [FIRST_CONNECT_TRIES] tries
     * (it may not be a heart rate monitor at all). With [waitForMonitor] (used by the main Connect
     * button) it keeps looking for [WAIT_FOR_MONITOR_MS] instead, so the rider can switch on heart
     * rate sharing on the watch after tapping Connect.
     */
    fun connect(address: String, name: String?, waitForMonitor: Boolean = false) {
        stopScan()
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()
        sessionJob?.cancel()
        sessionJob = scope.launch {
            var attempt = 0
            var everConnected = false
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                attempt++
                // A device that never worked is probably not a heart rate monitor (e.g. earbuds picked
                // from the paired list), or sharing is off, so don't retry forever. Once it has worked, keep retrying.
                val giveUp = !everConnected && if (waitForMonitor) {
                    SystemClock.elapsedRealtime() - startedAt > WAIT_FOR_MONITOR_MS
                } else {
                    attempt > FIRST_CONNECT_TRIES
                }
                if (giveUp) {
                    log("HR: no heart rate from ${name ?: address}. Is heart rate sharing on?")
                    _state.value = State.Idle
                    return@launch
                }
                if (waitForMonitor && !everConnected && attempt == 2) {
                    log("HR: still looking for ${name ?: address}; turn on heart rate sharing (Connected Fitness)")
                }
                _state.value = State.Connecting(name, attempt)
                log(if (attempt == 1) "HR: connecting to ${name ?: address}" else "HR: reconnect attempt $attempt")
                if (setUp(address)) {
                    everConnected = true
                    attempt = 0
                    _state.value = State.Connected(name)
                    log("HR: receiving heart rate from ${name ?: address}")
                    conn.connected.first { !it } // Suspend until the link drops.
                    log("HR: connection lost")
                    _state.value = State.Connecting(name, 1)
                }
                delay(1_000L + attempt.coerceAtMost(5) * 2_000L)
            }
        }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        conn.disconnect()
        _state.value = State.Idle
        _bpm.value = null
        log("HR: disconnected")
    }

    private suspend fun setUp(address: String): Boolean {
        val a = adapter
        if (a == null || !a.isEnabled) {
            log("HR: Bluetooth is off")
            return false
        }
        if (!conn.connect(a.getRemoteDevice(address))) return false
        if (!conn.enableNotifications(HeartRateUuids.SERVICE, HeartRateUuids.MEASUREMENT, indicate = false)) {
            log("HR: couldn't subscribe to heart rate")
            conn.disconnect()
            return false
        }
        return true
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val SCAN_DURATION_MS = 15_000L
        const val STALE_MS = 5_000L
        const val FIRST_CONNECT_TRIES = 3
        const val WAIT_FOR_MONITOR_MS = 3 * 60_000L
    }
}
