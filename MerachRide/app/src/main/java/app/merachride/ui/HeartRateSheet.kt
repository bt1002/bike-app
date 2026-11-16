package app.merachride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.merachride.HeartRateMonitor
import app.merachride.WatchLink

/**
 * Content of the heart rate bottom sheet: the MerachRide watch app, then a Bluetooth heart rate monitor
 * (Connected Fitness on the watch, or a chest strap) with connect/scan/disconnect.
 */
@Composable
fun HeartRatePanel(monitor: HeartRateMonitor, watchLink: WatchLink) {
    val state by monitor.state.collectAsStateWithLifecycle()
    val bpm by monitor.bpm.collectAsStateWithLifecycle()
    val found by monitor.found.collectAsStateWithLifecycle()
    val scanning by monitor.scanning.collectAsStateWithLifecycle()
    val autoWaiting by monitor.autoWaiting.collectAsStateWithLifecycle()
    var scannedOnce by remember { mutableStateOf(false) }
    val saved = remember(state) { monitor.savedDevice }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Heart rate", style = MaterialTheme.typography.titleMedium)

        WatchAppSection(watchLink, bpm)

        HorizontalDivider()
        Text("Bluetooth heart rate monitor", style = MaterialTheme.typography.titleSmall)

        if (saved != null) {
            var auto by remember { mutableStateOf(monitor.autoReconnect) }
            SwitchRow(
                "Connect to ${saved.name ?: saved.address} automatically when the bike connects",
                checked = auto,
                onChange = {
                    auto = it
                    monitor.autoReconnect = it
                },
            )
        }

        when (val s = state) {
            is HeartRateMonitor.State.Connected -> {
                Text("Connected · ${s.name ?: "monitor"}", color = MaterialTheme.colorScheme.primary)
                Text(
                    bpm?.let { "$it bpm" } ?: "Waiting for a reading…",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = monitor::disconnect) { Text("Disconnect") }
            }
            is HeartRateMonitor.State.Connecting -> {
                Text(
                    if (s.attempt > 1) "Reconnecting to ${s.name ?: "monitor"} (attempt ${s.attempt})…" else "Connecting to ${s.name ?: "monitor"}…",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                OutlinedButton(onClick = monitor::disconnect) { Text("Stop") }
            }
            HeartRateMonitor.State.Idle -> {
                if (autoWaiting && saved != null) {
                    Text(
                        "Waiting for ${saved.name ?: "the monitor"} to share heart rate. On a Pixel Watch, turn on " +
                            "Connected Fitness in quick settings; the app connects within about 10 seconds.",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = monitor::stopAutoConnect) { Text("Stop waiting") }
                }
                Text(
                    "On a Pixel Watch: swipe down for quick settings and tap Connected Fitness (or Settings → " +
                        "Connectivity → Share Heart Rate), then scan here. If the watch doesn't show up, pick it from " +
                        "\"Paired with this phone\" below. Chest straps work too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (saved != null) {
                    Button(onClick = { monitor.connect(saved.address, saved.name) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect to ${saved.name ?: saved.address}")
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (scanning) {
                            monitor.stopScan()
                        } else {
                            scannedOnce = true
                            monitor.startScan()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (scanning) "Stop scanning" else "Scan for heart rate monitors") }

                if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                found.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.name ?: "Unnamed monitor") },
                        supportingContent = { Text("${d.address} · ${d.rssi} dBm") },
                        trailingContent = { Text("Connect", color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { monitor.connect(d.address, d.name) },
                    )
                }
                if (scannedOnce && !scanning && found.isEmpty()) {
                    Text(
                        "No heart rate monitor found. Check the watch is sharing heart rate (or the strap is on " +
                            "and damp), and that it isn't already connected to another app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                val paired = remember { monitor.pairedDevices() }
                if (paired.isNotEmpty()) {
                    Text("Paired with this phone", style = MaterialTheme.typography.titleSmall)
                    paired.forEach { d ->
                        ListItem(
                            headlineContent = { Text(d.name ?: "Unnamed device") },
                            supportingContent = { Text(d.address) },
                            trailingContent = { Text("Connect", color = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { monitor.connect(d.address, d.name) },
                        )
                    }
                }
            }
        }
    }
}

/** The MerachRide watch app: status, start/stop, and whether it opens when the bike connects. */
@Composable
private fun WatchAppSection(link: WatchLink, bpm: Int?) {
    val state by link.state.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(link.autoStart) }

    Text("MerachRide watch app", style = MaterialTheme.typography.titleSmall)
    val (text, color) = when (val s = state) {
        WatchLink.State.Off ->
            "Not running. It reads heart rate on the watch and sends it here, no Connected Fitness needed." to
                MaterialTheme.colorScheme.onSurfaceVariant
        WatchLink.State.Opening -> "Opening the app on the watch…" to MaterialTheme.colorScheme.tertiary
        is WatchLink.State.Waiting ->
            "Open on ${s.watchName ?: "the watch"}, waiting for heart rate. Check the watch is on your wrist." to
                MaterialTheme.colorScheme.tertiary
        is WatchLink.State.Receiving ->
            "Receiving from ${s.watchName ?: "the watch"}" + (bpm?.let { " · $it bpm" } ?: "") to
                MaterialTheme.colorScheme.primary
        WatchLink.State.NotInstalled ->
            "No connected watch has the MerachRide watch app. Install it from Android Studio (see README)." to
                MaterialTheme.colorScheme.error
        is WatchLink.State.Failed -> "Couldn't start: ${s.reason}" to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)

    SwitchRow(
        "Open on the watch automatically when the bike connects",
        checked = auto,
        onChange = {
            auto = it
            link.autoStart = it
        },
    )
    val running = state is WatchLink.State.Opening || state is WatchLink.State.Waiting || state is WatchLink.State.Receiving
    if (running) {
        OutlinedButton(onClick = link::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop on watch") }
    } else {
        Button(onClick = { link.start() }, modifier = Modifier.fillMaxWidth()) { Text("Start on watch") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
