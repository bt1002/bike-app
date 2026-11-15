package app.merachride.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.merachride.BikeSession
import app.merachride.ConnState
import app.merachride.protocol.BikeData
import app.merachride.protocol.FtmsProtocol
import app.merachride.workout.SampleWorkouts
import app.merachride.workout.Workout
import app.merachride.workout.WorkoutEngine
import app.merachride.workout.WorkoutState
import kotlin.math.roundToInt

@Composable
fun RideScreen(session: BikeSession) {
    val state by session.state.collectAsStateWithLifecycle()
    val data by session.data.collectAsStateWithLifecycle()
    val target by session.targetLevel.collectAsStateWithLifecycle()
    val log by session.logLines.collectAsStateWithLifecycle()
    val workout by session.workout.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state, onDisconnect = session::disconnect)
            if (state is ConnState.Idle) {
                DevicePicker(session)
            } else {
                Metrics(data)
                LevelControl(
                    target = target,
                    enabled = state is ConnState.Ready,
                    onChange = session::changeLevel,
                )
                WorkoutPanel(workout, session.workout)
            }
            EventLog(log)
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(state: ConnState, onDisconnect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Merach Ride", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val (text, color) = when (state) {
                ConnState.Idle ->
                    "Not connected" to MaterialTheme.colorScheme.onSurfaceVariant
                is ConnState.Connecting ->
                    (if (state.attempt > 1) "Reconnecting (attempt ${state.attempt})…" else "Connecting to ${state.name ?: "bike"}…") to
                        MaterialTheme.colorScheme.tertiary
                is ConnState.Ready ->
                    "Connected · ${state.name ?: "bike"}" to MaterialTheme.colorScheme.primary
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        if (state !is ConnState.Idle) {
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
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
private fun Metrics(d: BikeData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Power", d.powerW?.toString(), "W", Modifier.weight(1f), big = true)
            Metric("Cadence", d.cadenceRpm?.roundToInt()?.toString(), "rpm", Modifier.weight(1f), big = true)
            Metric("Bike level", d.resistanceLevel?.toString(), "", Modifier.weight(1f), big = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Speed", d.speedKmh?.let { "%.1f".format(it) }, "km/h", Modifier.weight(1f))
            Metric("Heart rate", d.heartRateBpm?.takeIf { it > 0 }?.toString(), "bpm", Modifier.weight(1f))
            Metric("Energy", d.totalEnergyKcal?.toString(), "kcal", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Distance", d.totalDistanceM?.let { "%.2f".format(it / 1000.0) }, "km", Modifier.weight(1f))
            Metric("Bike time", d.elapsedTimeS?.let(::formatTime), "", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String?, unit: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value ?: "–",
                    style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (unit.isNotEmpty() && value != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ manual level

@Composable
private fun LevelControl(target: Int?, enabled: Boolean, onChange: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalButton(
                onClick = { onChange(-1) },
                enabled = enabled && (target ?: Int.MAX_VALUE) > FtmsProtocol.MIN_LEVEL,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("−", fontSize = 32.sp) }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Resistance", style = MaterialTheme.typography.labelLarge)
                Text(
                    target?.toString() ?: "–",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("of ${FtmsProtocol.MAX_LEVEL}", style = MaterialTheme.typography.bodySmall)
            }

            FilledTonalButton(
                onClick = { onChange(+1) },
                enabled = enabled && (target ?: Int.MIN_VALUE) < FtmsProtocol.MAX_LEVEL,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("+", fontSize = 32.sp) }
        }
    }
}

// ------------------------------------------------------------------ workouts

@Composable
private fun WorkoutPanel(ws: WorkoutState, engine: WorkoutEngine) {
    val w = ws.workout
    if (w == null) {
        WorkoutPicker(onPick = engine::load)
    } else {
        ActiveWorkout(ws, w, engine)
    }
}

@Composable
private fun WorkoutPicker(onPick: (Workout) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Workouts", style = MaterialTheme.typography.titleMedium)
        SampleWorkouts.all.forEach { wk ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(wk) },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(wk.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatTime(wk.totalSec), style = MaterialTheme.typography.bodyMedium)
                    }
                    WorkoutProfile(
                        wk,
                        progress = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkout(ws: WorkoutState, w: Workout, engine: WorkoutEngine) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${formatTime(ws.elapsedSec)} / ${formatTime(w.totalSec)}", style = MaterialTheme.typography.bodyMedium)
            }
            WorkoutProfile(
                w,
                progress = ws.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )

            val seg = ws.currentSegment
            if (ws.finished) {
                Text("Workout complete. Nice work!", style = MaterialTheme.typography.titleMedium)
            } else if (seg != null) {
                Text(
                    "Segment ${ws.segmentIndex + 1} of ${w.segments.size}: ${seg.label} · level ${seg.level}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatTime(ws.segmentRemainingSec),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "  left in segment",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                ws.nextSegment?.let { next ->
                    Text(
                        "Next: ${next.label} · level ${next.level} · ${formatTime(next.durationSec)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
}

/** Bar chart of a workout's levels, with completed segments drawn solid and a "now" marker. */
@Composable
private fun WorkoutProfile(w: Workout, progress: Float?, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val total = w.totalSec.toFloat()
        var x = 0f
        w.segments.forEach { s ->
            val segWidth = size.width * s.durationSec / total
            val h = size.height * s.level / FtmsProtocol.MAX_LEVEL
            val done = progress != null && (x + segWidth) / size.width <= progress
            val alpha = when {
                progress == null -> 0.8f
                done -> 1f
                else -> 0.35f
            }
            drawRect(
                color = bar.copy(alpha = alpha),
                topLeft = Offset(x, size.height - h),
                size = Size((segWidth - 1f).coerceAtLeast(1f), h),
            )
            x += segWidth
        }
        if (progress != null) {
            val px = size.width * progress
            drawLine(marker, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

// ------------------------------------------------------------------ log

@Composable
private fun EventLog(lines: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide connection log" else "Show connection log (${lines.size})")
        }
        if (expanded) {
            lines.take(40).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
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
