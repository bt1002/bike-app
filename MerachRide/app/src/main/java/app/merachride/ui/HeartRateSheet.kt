package app.merachride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.merachride.HeartRateMonitor

/** Content of the heart rate bottom sheet: status, and connect/scan/disconnect. */
@Composable
fun HeartRatePanel(monitor: HeartRateMonitor) {
    val state by monitor.state.collectAsStateWithLifecycle()
    val bpm by monitor.bpm.collectAsStateWithLifecycle()
    val found by monitor.found.collectAsStateWithLifecycle()
    val scanning by monitor.scanning.collectAsStateWithLifecycle()
    var scannedOnce by remember { mutableStateOf(false) }
    val saved = remember(state) { monitor.savedDevice }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Heart rate monitor", style = MaterialTheme.typography.titleMedium)

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
