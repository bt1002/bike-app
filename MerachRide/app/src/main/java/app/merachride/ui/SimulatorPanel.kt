package app.merachride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.merachride.BikeSession
import app.merachride.sim.TimeScale
import kotlin.math.roundToInt

/** Debug controls for the simulated bike: act like the rider or the bike to test the app's reactions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorPanel(session: BikeSession) {
    var cadence by rememberSaveable { mutableFloatStateOf(85f) }
    var pedalling by rememberSaveable { mutableStateOf(true) }
    var speed by rememberSaveable { mutableIntStateOf(TimeScale.factor) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()) // The sheet opens half-height; the last row must be reachable.
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Simulated bike", style = MaterialTheme.typography.titleMedium)
        Text(
            "Debug builds only. Data and level changes go through the same code as the real bike, without Bluetooth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bike's own buttons", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { session.simPressBikeButton(-1) }) { Text("−") }
            OutlinedButton(onClick = { session.simPressBikeButton(+1) }) { Text("+") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pedalling", modifier = Modifier.weight(1f))
            Switch(checked = pedalling, onCheckedChange = {
                pedalling = it
                session.simSetPedalling(it)
            })
        }

        Column {
            Text("Cadence: ${cadence.roundToInt()} rpm")
            Slider(
                value = cadence,
                onValueChange = {
                    cadence = it
                    session.simSetCadence(it.toDouble())
                },
                valueRange = 40f..120f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Time speed (workout timer, ride recording and the bike)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 30).forEach { f ->
                    FilterChip(
                        selected = speed == f,
                        onClick = {
                            speed = f
                            TimeScale.factor = f
                        },
                        label = { Text("$f×") },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = session::simDropConnection) { Text("Drop connection") }
            OutlinedButton(onClick = session::simResetCounters) { Text("Reset bike counters") }
        }
    }
}
