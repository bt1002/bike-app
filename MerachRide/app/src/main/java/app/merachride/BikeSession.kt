package app.merachride

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import app.merachride.ble.BleConnection
import app.merachride.protocol.BikeController
import app.merachride.protocol.BikeData
import app.merachride.protocol.FtmsProtocol
import app.merachride.protocol.FtmsProtocol.ResultCode
import app.merachride.protocol.FtmsUuids
import app.merachride.protocol.IndoorBikeDataParser
import app.merachride.protocol.ManualChangeDetector
import app.merachride.protocol.ResistanceScale
import app.merachride.service.RideService
import app.merachride.workout.WorkoutEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed interface ConnState {
    data object Idle : ConnState
    data class Connecting(val name: String?, val attempt: Int) : ConnState
    data class Ready(val name: String?) : ConnState
}

data class FoundDevice(val address: String, val name: String?, val rssi: Int, val advertisesFtms: Boolean)

data class SavedDevice(val address: String, val name: String?)

/**
 * App-wide owner of the bike connection (one instance, created in [MerachRideApp]).
 *
 * Handles scanning, connecting, the FTMS control handshake, auto-reconnect, and resistance
 * commands. The UI and [RideService] observe its StateFlows.
 */
@SuppressLint("MissingPermission") // The UI gates everything behind the Bluetooth permissions.
class BikeSession(
    private val app: Context,
    private val scope: CoroutineScope,
) : BikeController {

    private val adapter: BluetoothAdapter? =
        app.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = app.getSharedPreferences("bike", Context.MODE_PRIVATE)
    private val conn = BleConnection(app) { message -> log(message) }

    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _data = MutableStateFlow(BikeData())
    override val data: StateFlow<BikeData> = _data.asStateFlow()

    /** The level the app last asked for (re-applied after a reconnect). */
    private val _targetLevel = MutableStateFlow<Int?>(null)
    val targetLevel: StateFlow<Int?> = _targetLevel.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    /** Connection log, newest first. */
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val workout = WorkoutEngine(scope, this)

    private var sessionJob: Job? = null
    private var scanJob: Job? = null
    private var scanCallback: ScanCallback? = null

    private val commandMutex = Mutex()
    private val levelMutex = Mutex()
    private var lastConnectedAddress: String? = null
    /** The Control Point command waiting for its `80 <op> <result>` indication. */
    private class PendingCommand(val opCode: Int, val result: CompletableDeferred<ResultCode>)
    @Volatile private var pending: PendingCommand? = null

    private val resistanceScale = ResistanceScale()
    private val manualChange = ManualChangeDetector()

    /** True when the workout was paused because the link dropped, so it resumes on reconnect. */
    @Volatile private var resumeWorkoutOnReconnect = false

    init {
        scope.launch {
            conn.notifications.collect { n ->
                when (n.uuid) {
                    FtmsUuids.INDOOR_BIKE_DATA -> onBikeData(n.value)
                    FtmsUuids.CONTROL_POINT -> onControlPointResponse(n.value)
                    FtmsUuids.STATUS -> onStatus(n.value)
                }
            }
        }
    }

    private fun onBikeData(bytes: ByteArray) {
        val unitBefore = resistanceScale.unit
        val rawBefore = _data.value.resistanceRaw
        _data.update { prev ->
            val d = IndoorBikeDataParser.parse(bytes, prev)
            d.copy(resistanceLevel = d.resistanceRaw?.let { resistanceScale.toLevel(it, _targetLevel.value) })
        }
        val now = _data.value
        val unitAfter = resistanceScale.unit
        if (unitAfter != unitBefore) {
            // Logged so the unverified unit question can be answered from the in-app log.
            log("Bike reports resistance in ${unitAfter.name.lowercase()} (raw ${now.resistanceRaw})")
        }
        if (now.resistanceRaw != rawBefore) {
            // Diagnostic (unverified behaviour): shows whether button presses on the bike reach 0x2AD2.
            log("Bike resistance raw ${now.resistanceRaw} (level ${now.resistanceLevel ?: "?"})")
        }
        manualChange.onReported(now.resistanceLevel, _targetLevel.value, SystemClock.elapsedRealtime())?.let { level ->
            // The rider changed the level on the bike. Follow it rather than fight it; a running
            // workout sets its own level again at the next segment.
            _targetLevel.value = level
            log("Level changed on the bike: $level")
        }
    }

    private fun onControlPointResponse(bytes: ByteArray) {
        val r = FtmsProtocol.parseControlPointResponse(bytes) ?: return
        val p = pending
        if (p != null && p.opCode == r.requestOpCode) {
            p.result.complete(r.result)
        } else {
            // e.g. a late reply to a command that already timed out. Don't let it answer the wrong command.
            log("Ignored stray Control Point reply for op 0x%02X".format(r.requestOpCode))
        }
    }

    // ---------------------------------------------------------------- scanning

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    val lastDevice: SavedDevice?
        get() = prefs.getString(KEY_ADDRESS, null)?.let { SavedDevice(it, prefs.getString(KEY_NAME, null)) }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothOn()) {
            log("Bluetooth is off")
            return
        }
        stopScan()
        _found.value = emptyList()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onFound(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onFound)
            override fun onScanFailed(errorCode: Int) {
                log("Scan failed (code $errorCode)")
                _scanning.value = false
            }
        }
        scanCallback = cb
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, cb)
        _scanning.value = true
        log("Scanning…")
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
        val name = r.scanRecord?.deviceName ?: r.device.name
        val ftms = r.scanRecord?.serviceUuids?.any { it.uuid == FtmsUuids.SERVICE } == true
        val looksLikeMerach = name?.startsWith("MRK", ignoreCase = true) == true
        if (!ftms && !looksLikeMerach) return
        val device = FoundDevice(r.device.address, name, r.rssi, ftms)
        _found.update { list ->
            (list.filterNot { it.address == device.address } + device).sortedByDescending { it.rssi }
        }
    }

    // ---------------------------------------------------------------- connection

    /** Connects to the bike and keeps reconnecting until [disconnect] is called. */
    fun connect(address: String, name: String?) {
        stopScan()
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()
        sessionJob?.cancel()
        if (address != lastConnectedAddress) resistanceScale.reset() // Another bike may use other units.
        lastConnectedAddress = address
        ContextCompat.startForegroundService(app, Intent(app, RideService::class.java))

        sessionJob = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                _state.value = ConnState.Connecting(name, attempt)
                log(if (attempt == 1) "Connecting to ${name ?: address}" else "Reconnect attempt $attempt")
                if (setUp(address)) {
                    attempt = 0
                    _state.value = ConnState.Ready(name)
                    resumeWorkoutIfPausedByDrop()
                    conn.connected.first { !it } // Suspend until the link drops.
                    log("Connection lost")
                    // Leave Ready right away so the UI and setResistanceLevel() don't act on a dead link during the backoff.
                    _state.value = ConnState.Connecting(name, 1)
                    pauseWorkoutForDrop()
                }
                delay(1_000L + attempt.coerceAtMost(5) * 2_000L)
            }
        }
    }

    private suspend fun pauseWorkoutForDrop(): Unit = withContext(Dispatchers.Main) {
        if (workout.state.value.running) {
            workout.pause()
            resumeWorkoutOnReconnect = true
            log("Workout paused until the bike reconnects")
        }
    }

    private suspend fun resumeWorkoutIfPausedByDrop(): Unit = withContext(Dispatchers.Main) {
        if (resumeWorkoutOnReconnect) {
            resumeWorkoutOnReconnect = false
            workout.start() // No-op if the user ended or restarted the workout meanwhile.
            log("Workout resumed")
        }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        resumeWorkoutOnReconnect = false
        workout.pause()
        conn.disconnect()
        _state.value = ConnState.Idle
        _data.value = BikeData()
        app.stopService(Intent(app, RideService::class.java))
        log("Disconnected")
    }

    /** Connect + the FTMS handshake verified by hand in nRF Connect. */
    private suspend fun setUp(address: String): Boolean {
        val a = adapter
        if (a == null || !a.isEnabled) {
            log("Bluetooth is off")
            return false
        }
        if (!conn.connect(a.getRemoteDevice(address))) return false
        _data.value = BikeData()
        manualChange.reset()

        if (!conn.enableNotifications(FtmsUuids.SERVICE, FtmsUuids.INDOOR_BIKE_DATA, indicate = false)) {
            log("Warning: no live data subscription")
        }
        conn.enableNotifications(FtmsUuids.SERVICE, FtmsUuids.STATUS, indicate = false)
        if (!conn.enableNotifications(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, indicate = true)) {
            log("Could not enable Control Point indications")
            conn.disconnect()
            return false
        }

        val control = sendCommand(FtmsProtocol.requestControl())
        if (control != ResultCode.SUCCESS) {
            log("Request Control failed: $control. Is the Merach app connected?")
            conn.disconnect()
            return false
        }
        val start = sendCommand(FtmsProtocol.startOrResume())
        if (start != ResultCode.SUCCESS) log("Start/Resume returned $start (continuing)")

        _targetLevel.value?.let { level ->
            val r = sendCommand(FtmsProtocol.setResistanceLevel(level))
            log("Re-applied level $level: $r")
        }
        log("Ready: app has control of the bike")
        return true
    }

    // ---------------------------------------------------------------- control

    override suspend fun setResistanceLevel(level: Int): Boolean {
        _targetLevel.value = level.coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL)
        return sendTargetLevel()
    }

    /** +1 / -1 from the UI buttons. The target updates at once, so fast taps add up correctly. */
    fun changeLevel(delta: Int) {
        _targetLevel.update { current ->
            val base = current ?: _data.value.resistanceLevel ?: FtmsProtocol.MIN_LEVEL
            (base + delta).coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL)
        }
        scope.launch { sendTargetLevel() }
    }

    /**
     * Sends whatever [targetLevel] is *now*. Callers may run out of order, but each one sends
     * the latest target, so the bike always ends on the last level asked for.
     */
    private suspend fun sendTargetLevel(): Boolean = levelMutex.withLock {
        val l = _targetLevel.value ?: return@withLock false
        if (_state.value !is ConnState.Ready) return@withLock false // Applied in setUp() on reconnect.

        var result = sendCommand(FtmsProtocol.setResistanceLevel(l))
        if (result == ResultCode.CONTROL_NOT_PERMITTED) {
            log("Control was lost; requesting it again")
            sendCommand(FtmsProtocol.requestControl())
            result = sendCommand(FtmsProtocol.setResistanceLevel(l))
        }
        if (result == ResultCode.SUCCESS) log("Level $l") else log("Set level $l failed: $result")
        result == ResultCode.SUCCESS
    }

    /** Writes one Control Point command and waits for its `80 <op> <result>` indication. */
    private suspend fun sendCommand(bytes: ByteArray): ResultCode = commandMutex.withLock {
        val p = PendingCommand(bytes[0].toInt() and 0xFF, CompletableDeferred())
        pending = p
        manualChange.onCommandSent(SystemClock.elapsedRealtime())
        try {
            if (!conn.write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, bytes)) {
                return@withLock ResultCode.WRITE_FAILED
            }
            withTimeoutOrNull(COMMAND_TIMEOUT_MS) { p.result.await() } ?: ResultCode.TIMEOUT
        } finally {
            pending = null
            manualChange.onCommandSent(SystemClock.elapsedRealtime()) // The bike's report lags the reply too.
        }
    }

    private fun onStatus(bytes: ByteArray) {
        FtmsProtocol.describeStatus(bytes)?.let { log("Bike: $it") }
        val op = bytes.firstOrNull()?.toInt()?.and(0xFF)
        if (op == FtmsProtocol.STATUS_CONTROL_LOST && _state.value is ConnState.Ready) {
            scope.launch {
                val r = sendCommand(FtmsProtocol.requestControl())
                log("Re-requested control: $r")
            }
        }
    }

    // ---------------------------------------------------------------- log

    fun log(message: String) {
        Log.i(TAG, message)
        val line = "${LocalTime.now().format(TIME_FORMAT)}  $message"
        _logLines.update { (listOf(line) + it).take(MAX_LOG_LINES) }
    }

    private companion object {
        const val TAG = "MerachRide"
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val SCAN_DURATION_MS = 15_000L
        const val COMMAND_TIMEOUT_MS = 3_000L
        const val MAX_LOG_LINES = 100
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
