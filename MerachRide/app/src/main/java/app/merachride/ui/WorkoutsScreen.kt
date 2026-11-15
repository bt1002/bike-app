package app.merachride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.merachride.workout.SampleWorkouts
import app.merachride.workout.Workout

/** Lists the workouts; tapping one loads it on the Ride tab. */
@Composable
fun WorkoutsScreen(onPick: (Workout) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Workouts", style = MaterialTheme.typography.titleLarge) }
        items(SampleWorkouts.all) { w -> WorkoutCard(w, onClick = { onPick(w) }) }
    }
}

@Composable
fun WorkoutCard(w: Workout, onClick: () -> Unit, actions: (@Composable () -> Unit)? = null) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(w.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${formatTime(w.totalSec)} · ${w.segments.size} segments",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            WorkoutProfile(
                w,
                progress = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )
            actions?.invoke()
        }
    }
}
