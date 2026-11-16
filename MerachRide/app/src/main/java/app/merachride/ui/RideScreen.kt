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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import app.merachride.WatchLink
import app.merachride.history.RideProgress
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
    watchLink: WatchLink,
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
    var showLog by rememberSaveable { mutableStateOf(false) }
    var showHeartRate by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(
            state,
            hrState = hrState,
            hrLive = hrBpm != null,
            onShowHeartRate = { showHeartRate = true },
            onShowLog = { showLog = true },
            onDisconnect = session::disconnect,
        )
        if (state is ConnState.Idle) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) { DevicePicker(session) }
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
                        RideBar(ride, onFinish = tracker::finish)
                    }
                    workoutArea(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                live(Modifier.fillMaxWidth())
                RideBar(ride, onFinish = tracker::finish)
                workoutArea(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    if (showHeartRate) {
        ModalBottomSheet(onDismissRequest = {
            heartRate.stopScan()
            showHeartRate = false
        }) {
            HeartRatePanel(heartRate, watchLink)
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
    hrLive: Boolean,
    onShowHeartRate: () -> Unit,
    onShowLog: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Merach Ride", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val (text, color) = when (state) {
                ConnState.Idle ->
                    "Not connected" to MaterialTheme.colorScheme.onSurfaceVariant
                is ConnState.Connecting ->
                    (if (state.attempt > 1) "Reconnecting (attempt ${state.attempt})…" else "Connecting to ${state.name ?: "bike"}…") to
                        MaterialTheme.colorScheme.tertiary
                is ConnState.Ready ->
                    "Connected · ${state.name ?: "bike"}" to MaterialTheme.colorScheme.primary
            }
            Text(text, color = color, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onShowHeartRate) {
            // Filled and coloured when a monitor is connected or the watch app is sending, outlined otherwise.
            if (hrState is HeartRateMonitor.State.Connected || hrLive) {
                Icon(Icons.Filled.Favorite, contentDescription = "Heart rate monitor (connected)", tint = MaterialTheme.colorScheme.error)
            } else {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Heart rate monitor")
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

// ------------------------------------------------------------------ connect

@Composable
private fun DevicePicker(session: BikeSession) {
    val found by session.found.collectAsStateWithLifecycle()
    val scanning by session.scanning.collectAsStateWithLifecycle()
    var scannedOnce by rememberSaveable { mutableStateOf(false) }
    val last = remember { session.lastDevice }

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
                    onClick = { withBluetooth { session.connect(last.address, last.name) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect to ${last.name ?: last.address}") }
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

            found.forEach { d ->
                ListItem(
                    headlineContent = { Text(d.name ?: "Unnamed device") },
                    supportingContent = {
                        Text("${d.address} · ${d.rssi} dBm" + if (d.advertisesFtms) " · FTMS" else "")
                    },
                    trailingContent = { Text("Connect", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { session.connect(d.address, d.name) },
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
            SmallMetric("km/h", data.speedKmh?.let { "%.1f".format(it) }, Modifier.weight(1f))
            SmallMetric("bpm", data.heartRateBpm?.takeIf { it > 0 }?.toString(), Modifier.weight(1f))
            SmallMetric("kcal", data.totalEnergyKcal?.toString(), Modifier.weight(1f))
            SmallMetric("km", data.totalDistanceM?.let { "%.2f".format(it / 1000.0) }, Modifier.weight(1f))
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
                "Ride ${formatTime(ride.elapsedSec)} · ${"%.2f".format(ride.distanceM / 1000.0)} km · ${ride.energyKcal} kcal",
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        }

        val seg = ws.currentSegment
        if (ws.finished) {
            Text("Workout complete. Nice work!", style = MaterialTheme.typography.titleLarge)
        } else if (seg != null) {
            Text(
                "${ws.segmentIndex + 1}/${w.segments.size} · ${seg.label.ifBlank { "Segment" }} · level ${seg.levelText}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(formatTime(ws.segmentRemainingSec), fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp)
            Text(
                ws.nextSegment?.let { "Next: ${it.label.ifBlank { "Segment" }} · level ${it.levelText} · ${formatTime(it.durationSec)}" }
                    ?: "Last segment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        WorkoutProfile(
            w,
            progress = ws.progress,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(4.dp))
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

/** Bar chart of a workout's levels, with completed segments drawn solid and a "now" marker. */
@Composable
fun WorkoutProfile(w: Workout, progress: Float?, modifier: Modifier = Modifier) {
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
            val hStart = size.height * s.level / FtmsProtocol.MAX_LEVEL
            val hEnd = size.height * (s.endLevel ?: s.level) / FtmsProtocol.MAX_LEVEL
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
