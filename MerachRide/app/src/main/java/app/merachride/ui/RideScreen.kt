package app.merachride.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.merachride.BikeSession
import app.merachride.ConnState
import app.merachride.HeartRateMonitor
import app.merachride.data.JsonFileStore
import app.merachride.history.PaceTarget
import app.merachride.history.RideProgress
import app.merachride.history.RideRecord
import app.merachride.history.RideTracker
import app.merachride.protocol.BikeData
import app.merachride.protocol.FtmsProtocol
import app.merachride.workout.Workout
import app.merachride.workout.WorkoutEngine
import app.merachride.workout.WorkoutState
import kotlin.math.roundToInt

/**
 * The screen used while riding: everything fits without scrolling, the most important numbers
 * are the biggest, and in landscape the live stats and the workout sit side by side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideScreen(
    session: BikeSession,
    tracker: RideTracker,
    heartRate: HeartRateMonitor,
    history: JsonFileStore<RideRecord>,
    onChooseWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val data by session.data.collectAsStateWithLifecycle()
    val target by session.targetLevel.collectAsStateWithLifecycle()
    val log by session.logLines.collectAsStateWithLifecycle()
    val workout by session.workout.state.collectAsStateWithLifecycle()
    val ride by tracker.current.collectAsStateWithLifecycle()
    val hrState by heartRate.state.collectAsStateWithLifecycle()
    val hrBpm by heartRate.bpm.collectAsStateWithLifecycle()
    val rides by history.items.collectAsStateWithLifecycle()
    val loaded = workout.workout
    val paceTarget = remember(rides, loaded?.key) { loaded?.let { PaceTarget.forWorkout(rides, it) } }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var showHeartRate by rememberSaveable { mutableStateOf(false) }
    var showSimulator by rememberSaveable { mutableStateOf(false) }
    val simulated by session.simulated.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(
            state,
            hrState = hrState,
            onShowHeartRate = { showHeartRate = true },
            onShowSimulator = if (simulated) ({ showSimulator = true }) else null,
            onShowLog = { showLog = true },
            onDisconnect = {
                session.disconnect()
                if (heartRate.state.value !is HeartRateMonitor.State.Idle) heartRate.disconnect()
            },
        )
        // Warn while riding without heart rate, since rides would be saved without it.
        if (state !is ConnState.Idle && hrState !is HeartRateMonitor.State.Connected) {
            val hasSavedMonitor = remember(hrState) { heartRate.savedDevice != null }
            WatchWarning(hrState, hasSavedMonitor, onClick = { showHeartRate = true })
        }
        if (state is ConnState.Idle) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) { DevicePicker(session, heartRate) }
        } else {
            val live: @Composable (Modifier) -> Unit = { m ->
                LiveStats(
                    // A separate heart rate monitor wins over the bike's own reading.
                    data = hrBpm?.let { data.copy(heartRateBpm = it) } ?: data,
                    target = target,
                    levelEnabled = state is ConnState.Ready,
                    onChangeLevel = session::changeLevel,
                    modifier = m,
                )
            }
            val workoutArea: @Composable (Modifier) -> Unit = { m ->
                WorkoutArea(workout, session.workout, onChooseWorkout, m)
            }
            val config = LocalConfiguration.current
            if (config.screenWidthDp > config.screenHeightDp) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        live(Modifier.fillMaxWidth())
                        PacePanel(ride, data.speedKmh, hrBpm ?: data.heartRateBpm, loaded, paceTarget)
                        RideBar(ride, onFinish = tracker::finish)
                    }
                    workoutArea(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                live(Modifier.fillMaxWidth())
                PacePanel(ride, data.speedKmh, hrBpm ?: data.heartRateBpm, loaded, paceTarget)
                RideBar(ride, onFinish = tracker::finish)
                workoutArea(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    if (showSimulator && simulated) {
        ModalBottomSheet(onDismissRequest = { showSimulator = false }) {
            SimulatorPanel(session)
        }
    }

    if (showHeartRate) {
        ModalBottomSheet(onDismissRequest = {
            heartRate.stopScan()
            showHeartRate = false
        }) {
            HeartRatePanel(heartRate)
        }
    }

    if (showLog) {
        ModalBottomSheet(onDismissRequest = { showLog = false }) {
            Text(
                "Connection log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                items(log) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(
    state: ConnState,
    hrState: HeartRateMonitor.State,
    onShowHeartRate: () -> Unit,
    /** Non-null while connected to the simulated bike: opens its debug controls. */
    onShowSimulator: (() -> Unit)?,
    onShowLog: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Merach Ride", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // Two status lights: green = connected, amber = connecting, red = watch missing mid-ride.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusLight(
                    label = "Bike",
                    light = when (state) {
                        is ConnState.Ready -> Light.ON
                        is ConnState.Connecting -> Light.PENDING
                        ConnState.Idle -> Light.OFF
                    },
                )
                StatusLight(
                    label = "Watch",
                    light = when (hrState) {
                        is HeartRateMonitor.State.Connected -> Light.ON
                        is HeartRateMonitor.State.Connecting -> Light.PENDING
                        HeartRateMonitor.State.Idle -> if (state is ConnState.Idle) Light.OFF else Light.ALERT
                    },
                    onClick = onShowHeartRate,
                )
            }
        }
        if (onShowSimulator != null) {
            IconButton(onClick = onShowSimulator) {
                Icon(Icons.Filled.Build, contentDescription = "Simulated bike controls")
            }
        }
        IconButton(onClick = onShowLog) {
            Icon(Icons.Outlined.Info, contentDescription = "Connection log")
        }
        if (state !is ConnState.Idle) {
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
}

private enum class Light { ON, PENDING, OFF, ALERT }

/** A small "● Label" pill whose dot shows a connection's state. */
@Composable
private fun StatusLight(label: String, light: Light, onClick: (() -> Unit)? = null) {
    val dot = when (light) {
        Light.ON -> AHEAD_GREEN
        Light.PENDING -> PENDING_AMBER
        Light.OFF -> MaterialTheme.colorScheme.outline
        Light.ALERT -> MaterialTheme.colorScheme.error
    }
    val stateWord = when (light) {
        Light.ON -> "connected"
        Light.PENDING -> "connecting"
        Light.OFF -> "not connected"
        Light.ALERT -> "not connected"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$label $stateWord" },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(10.dp)) { drawCircle(dot) }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** One-line warning while the bike is connected but the watch (heart rate) isn't. Tap to open the heart rate sheet. */
@Composable
private fun WatchWarning(hrState: HeartRateMonitor.State, hasSavedMonitor: Boolean, onClick: () -> Unit) {
    val (text, colour) = when {
        hrState is HeartRateMonitor.State.Connecting ->
            "Looking for your watch… turn on Connected Fitness on it." to MaterialTheme.colorScheme.tertiary
        hasSavedMonitor ->
            "Watch not connected: no heart rate. Turn on Connected Fitness, then tap here." to MaterialTheme.colorScheme.error
        else ->
            "No heart rate monitor. Tap here to connect your watch." to MaterialTheme.colorScheme.error
    }
    Text(
        text,
        color = colour,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private val PENDING_AMBER = Color(0xFFF9A825)

// ------------------------------------------------------------------ connect

@Composable
private fun DevicePicker(session: BikeSession, heartRate: HeartRateMonitor) {
    val found by session.found.collectAsStateWithLifecycle()
    val scanning by session.scanning.collectAsStateWithLifecycle()
    var scannedOnce by rememberSaveable { mutableStateOf(false) }
    val last = remember { session.lastDevice }
    val lastHr = remember { heartRate.savedDevice }

    /** Connects the bike, plus the last heart rate monitor (e.g. the watch) if there is one. */
    fun connectAll(address: String, name: String?) {
        session.connect(address, name)
        val hr = lastHr ?: return
        // Keep looking for a few minutes, so heart rate sharing can be switched on after tapping Connect.
        if (heartRate.state.value is HeartRateMonitor.State.Idle) heartRate.connect(hr.address, hr.name, waitForMonitor = true)
    }

    // Whatever the user tapped while Bluetooth was off, run it once it's on.
    var afterEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (session.isBluetoothOn()) afterEnable?.invoke()
        afterEnable = null
    }
    fun withBluetooth(action: () -> Unit) {
        if (session.isBluetoothOn()) {
            action()
        } else {
            afterEnable = action
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect to your bike", style = MaterialTheme.typography.titleMedium)
            Text(
                "Close the Merach app first. The bike takes commands from one app at a time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (last != null) {
                Button(
                    onClick = { withBluetooth { connectAll(last.address, last.name) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Connect to ${last.name ?: last.address}" +
                            (lastHr?.let { " + ${it.name ?: "heart rate"}" } ?: ""),
                    )
                }
                if (lastHr != null) {
                    Text(
                        "Turn on heart rate sharing on the watch first (quick settings → Connected Fitness).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    if (scanning) {
                        session.stopScan()
                    } else {
                        withBluetooth {
                            scannedOnce = true
                            session.startScan()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (scanning) "Stop scanning" else "Scan for bikes") }

            if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (session.simulatorAvailable) {
                OutlinedButton(onClick = session::connectSimulated, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to simulated bike (debug)")
                }
            }

            found.forEach { d ->
                ListItem(
                    headlineContent = { Text(d.name ?: "Unnamed device") },
                    supportingContent = {
                        Text("${d.address} · ${d.rssi} dBm" + if (d.advertisesFtms) " · FTMS" else "")
                    },
                    trailingContent = { Text("Connect", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { connectAll(d.address, d.name) },
                )
            }
            if (scannedOnce && !scanning && found.isEmpty()) {
                Text(
                    "No bike found. Make sure it's powered on (pedal to wake it) and the Merach app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ live data

@Composable
private fun LiveStats(
    data: BikeData,
    target: Int?,
    levelEnabled: Boolean,
    onChangeLevel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigMetric("Power", data.powerW?.toString(), "W", Modifier.weight(1f))
            BigMetric("Cadence", data.cadenceRpm?.roundToInt()?.toString(), "rpm", Modifier.weight(1f))
        }
        LevelControl(target, levelEnabled, onChangeLevel)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallMetric(Units.SPEED, data.speedKmh?.let(Units::speed), Modifier.weight(1f))
            SmallMetric("bpm", data.heartRateBpm?.takeIf { it > 0 }?.toString(), Modifier.weight(1f))
            SmallMetric("kcal", data.totalEnergyKcal?.toString(), Modifier.weight(1f))
            SmallMetric(Units.DISTANCE, data.totalDistanceM?.let(Units::distance), Modifier.weight(1f))
            SmallMetric("time", data.elapsedTimeS?.let(::formatTime), Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigMetric(label: String, value: String?, unit: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value ?: "–", fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
                if (value != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallMetric(unit: String, value: String?, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value ?: "–",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelControl(target: Int?, enabled: Boolean, onChange: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalButton(
                onClick = { onChange(-1) },
                enabled = enabled && (target ?: Int.MAX_VALUE) > FtmsProtocol.MIN_LEVEL,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("−", fontSize = 36.sp) }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Level", style = MaterialTheme.typography.labelLarge)
                Text(target?.toString() ?: "–", fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
                Text("of ${FtmsProtocol.MAX_LEVEL}", style = MaterialTheme.typography.bodySmall)
            }

            FilledTonalButton(
                onClick = { onChange(+1) },
                enabled = enabled && (target ?: Int.MIN_VALUE) < FtmsProtocol.MAX_LEVEL,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("+", fontSize = 36.sp) }
        }
    }
}

// ------------------------------------------------------------------ pace

/**
 * Speed and heart rate: now, this ride's average so far, and the target (average of the last 5 rides
 * of the loaded workout). The arrow compares this ride's average with the target.
 */
@Composable
private fun PacePanel(
    ride: RideProgress?,
    speedNow: Double?,
    hrNow: Int?,
    workout: Workout?,
    target: PaceTarget?,
) {
    // Wait for a short stretch of riding so the arrow doesn't swing on the first few samples.
    val settled = (ride?.elapsedSec ?: 0) >= PACE_SETTLE_SEC
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row {
                PaceHeader("", Modifier.weight(1.1f))
                PaceHeader("Now", Modifier.weight(1f))
                PaceHeader("Avg", Modifier.weight(1f))
                PaceHeader("Target", Modifier.weight(1.3f))
            }
            PaceRow(
                label = Units.SPEED,
                now = speedNow?.let(Units::speed),
                avg = ride?.avgSpeedKmh?.let(Units::speed),
                target = target?.avgSpeedKmh?.let(Units::speed),
                trend = if (settled) PaceTarget.trend(ride?.avgSpeedKmh, target?.avgSpeedKmh) else null,
                colouredTrend = true,
            )
            PaceRow(
                label = "bpm",
                now = hrNow?.takeIf { it > 0 }?.toString(),
                avg = ride?.avgHeartRateBpm?.toString(),
                target = target?.avgHeartRateBpm?.toString(),
                trend = if (settled) {
                    PaceTarget.trend(ride?.avgHeartRateBpm?.toDouble(), target?.avgHeartRateBpm?.toDouble())
                } else {
                    null
                },
                // A higher heart rate isn't better or worse in itself, so its arrow stays neutral.
                colouredTrend = false,
            )
            val note = when {
                workout == null -> "Pick a workout to see a target from your past rides of it."
                target == null -> "No past rides of this workout yet. This ride sets the first target."
                else -> "Target: average of your last ${target.rideCount} ride${if (target.rideCount == 1) "" else "s"} of this workout."
            }
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaceHeader(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PaceRow(
    label: String,
    now: String?,
    avg: String?,
    target: String?,
    trend: PaceTarget.Trend?,
    colouredTrend: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.labelLarge)
        Text(now ?: "–", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(avg ?: "–", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
            Text(target ?: "–", style = MaterialTheme.typography.titleMedium)
            if (trend != null) {
                val (symbol, colour, description) = when (trend) {
                    PaceTarget.Trend.AHEAD -> Triple(" ▲", if (colouredTrend) AHEAD_GREEN else MaterialTheme.colorScheme.onSurfaceVariant, "above target")
                    PaceTarget.Trend.BEHIND -> Triple(" ▼", if (colouredTrend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, "below target")
                    PaceTarget.Trend.ON_PACE -> Triple(" =", MaterialTheme.colorScheme.onSurfaceVariant, "on target")
                }
                Text(
                    symbol,
                    color = colour,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

private const val PACE_SETTLE_SEC = 30
private val AHEAD_GREEN = Color(0xFF2E7D32)

// ------------------------------------------------------------------ ride

@Composable
private fun RideBar(ride: RideProgress?, onFinish: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (ride == null) {
            Text(
                "Start pedalling to begin recording a ride",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Ride ${formatTime(ride.elapsedSec)} · ${Units.distance(ride.distanceM)} ${Units.DISTANCE} · ${ride.energyKcal} kcal",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { confirm = true }) { Text("Finish ride") }
        }
    }
    if (confirm && ride != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Finish this ride?") },
            text = {
                Text(
                    if (ride.elapsedSec >= 60) "It will be saved to History." else "Rides under a minute aren't saved.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onFinish()
                }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep riding") } },
        )
    }
}

// ------------------------------------------------------------------ workout

@Composable
private fun WorkoutArea(ws: WorkoutState, engine: WorkoutEngine, onChoose: () -> Unit, modifier: Modifier) {
    val w = ws.workout
    Card(modifier) {
        if (w == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Free ride", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Set the level yourself, or pick a workout to have the app change it for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onChoose) { Text("Choose a workout") }
            }
        } else {
            ActiveWorkout(ws, w, engine)
        }
    }
}

@Composable
private fun ActiveWorkout(ws: WorkoutState, w: Workout, engine: WorkoutEngine) {
    // Compact so the profile chart always gets a usable height, even when the pace panel and stats
    // leave the card little room (it used to be squeezed to nothing on a Pixel 10).
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                w.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${formatTime(ws.totalRemainingSec)} left", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            // Shift the rest of the workout down or up a level. In the title row so the card stays
            // short enough for the chart and the Start/Skip/End buttons.
            FilledTonalIconButton(
                onClick = { engine.bumpLevels(-1) },
                enabled = !ws.finished,
                modifier = Modifier.size(36.dp),
            ) { Text("−", fontSize = 20.sp) }
            Text(
                if (ws.levelOffset == 0) "±0" else offsetSuffix(ws.levelOffset).trim(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .semantics { contentDescription = "Rest of workout shifted by ${ws.levelOffset} levels" },
            )
            FilledTonalIconButton(
                onClick = { engine.bumpLevels(+1) },
                enabled = !ws.finished,
                modifier = Modifier.size(36.dp),
            ) { Text("+", fontSize = 20.sp) }
        }

        val seg = ws.currentSegment
        if (ws.finished) {
            Text("Workout complete. Nice work!", style = MaterialTheme.typography.titleLarge)
        } else if (seg != null) {
            // Segment countdown on the left, what's now and what's next beside it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(ws.segmentRemainingSec), fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${ws.segmentIndex + 1}/${w.segments.size} · ${seg.label.ifBlank { "Segment" }} · level ${seg.levelText}${offsetSuffix(ws.levelOffset)}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        ws.nextSegment?.let { "Next: ${it.label.ifBlank { "Segment" }} · level ${it.levelText}${offsetSuffix(ws.levelOffset)} · ${formatTime(it.durationSec)}" }
                            ?: "Last segment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Fixed height rather than "whatever is left", so it can't collapse to zero.
        WorkoutProfile(
            w,
            progress = ws.progress,
            offset = ws.levelOffset,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                ws.finished -> Button(onClick = engine::start) { Text("Restart") }
                ws.running -> Button(onClick = engine::pause) { Text("Pause") }
                else -> Button(onClick = engine::start) { Text(if (ws.elapsedSec > 0) "Resume" else "Start") }
            }
            OutlinedButton(onClick = engine::skip, enabled = !ws.finished) { Text("Skip") }
            TextButton(onClick = engine::unload) { Text("End") }
        }
    }
}

/** " +2" / " −1" after a level when the rider has shifted the workout, empty otherwise. */
private fun offsetSuffix(offset: Int): String = when {
    offset > 0 -> " +$offset"
    offset < 0 -> " −${-offset}"
    else -> ""
}

/**
 * Bar chart of a workout's levels, with completed segments drawn solid and a "now" marker.
 * Segments not yet finished are drawn shifted by [offset] (the rider's +/−), as the bike will get them.
 */
@Composable
fun WorkoutProfile(w: Workout, progress: Float?, modifier: Modifier = Modifier, offset: Int = 0) {
    val bar = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val total = w.totalSec.toFloat()
        var x = 0f
        w.segments.forEach { s ->
            val segWidth = size.width * s.durationSec / total
            val done = progress != null && (x + segWidth) / size.width <= progress
            val alpha = when {
                progress == null -> 0.8f
                done -> 1f
                else -> 0.35f
            }
            val shift = if (done) 0 else offset
            fun h(level: Int) = size.height * (level + shift).coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL) / FtmsProtocol.MAX_LEVEL
            val hStart = h(s.level)
            val hEnd = h(s.endLevel ?: s.level)
            val right = x + (segWidth - 1f).coerceAtLeast(1f)
            // A held level is a rectangle; a ramp is a sloped top.
            val shape = Path().apply {
                moveTo(x, size.height)
                lineTo(x, size.height - hStart)
                lineTo(right, size.height - hEnd)
                lineTo(right, size.height)
                close()
            }
            drawPath(shape, color = bar.copy(alpha = alpha))
            x += segWidth
        }
        if (progress != null) {
            val px = size.width * progress
            drawLine(marker, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

fun formatTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return if (s >= 3600) {
        "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    } else {
        "%d:%02d".format(s / 60, s % 60)
    }
}
