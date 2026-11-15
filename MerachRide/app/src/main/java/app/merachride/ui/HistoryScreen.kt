package app.merachride.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.merachride.data.JsonFileStore
import app.merachride.history.RideRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm")

/** Saved rides, newest first, with all-time totals at the top. Tap a ride for details. */
@Composable
fun HistoryScreen(store: JsonFileStore<RideRecord>, modifier: Modifier = Modifier) {
    val rides by store.items.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<RideRecord?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("History", style = MaterialTheme.typography.titleLarge) }
        item { Totals(rides) }
        if (rides.isEmpty()) {
            item {
                Text(
                    "No rides yet. A ride starts when you pedal while connected and is saved when you tap " +
                        "Finish ride or disconnect (rides under a minute aren't kept).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(rides, key = { it.id }) { r ->
            RideCard(
                r,
                expanded = expanded == r.id,
                onClick = { expanded = if (expanded == r.id) null else r.id },
                onDelete = { confirmDelete = r },
            )
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this ride?") },
            text = { Text(formatDate(r.startEpochMs)) },
            confirmButton = {
                TextButton(onClick = {
                    store.update { list -> list.filterNot { it.id == r.id } }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Totals(rides: List<RideRecord>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Total("${rides.size}", "rides")
            Total(formatTime(rides.sumOf { it.durationSec }), "time")
            Total(Units.distanceShort(rides.sumOf { it.distanceM }), Units.DISTANCE)
            Total("${rides.sumOf { it.energyKcal }}", "kcal")
        }
    }
}

@Composable
private fun Total(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RideCard(r: RideRecord, expanded: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatDate(r.startEpochMs), style = MaterialTheme.typography.labelLarge)
            Text(r.workoutName ?: "Free ride", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatTime(r.durationSec)} · ${Units.distance(r.distanceM)} ${Units.DISTANCE} · ${r.energyKcal} kcal",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (expanded) {
                Detail("Moving time", formatTime(r.movingSec))
                Detail("Speed", "avg ${r.avgSpeedKmh?.let(Units::speed) ?: "–"} ${Units.SPEED}")
                Detail("Power", "avg ${r.avgPowerW ?: "–"} W · max ${r.maxPowerW ?: "–"} W")
                Detail("Cadence", "avg ${r.avgCadenceRpm ?: "–"} rpm")
                Detail("Heart rate", "avg ${r.avgHeartRateBpm ?: "–"} · max ${r.maxHeartRateBpm ?: "–"} bpm")
                TextButton(onClick = onDelete) { Text("Delete ride") }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDate(epochMs: Long): String =
    DATE_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
