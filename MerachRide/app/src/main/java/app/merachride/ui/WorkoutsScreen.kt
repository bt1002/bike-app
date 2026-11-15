package app.merachride.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.merachride.data.JsonFileStore
import app.merachride.protocol.FtmsProtocol
import app.merachride.workout.SampleWorkouts
import app.merachride.workout.SegmentDraft
import app.merachride.workout.Workout
import app.merachride.workout.WorkoutDraft
import java.util.UUID

/** Built-in and custom workouts. Tapping one loads it on the Ride tab; custom ones can be edited. */
@Composable
fun WorkoutsScreen(
    store: JsonFileStore<Workout>,
    onPick: (Workout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val custom by store.items.collectAsStateWithLifecycle()
    // Not saved across rotation: the editor is short-lived and losing a half-typed draft on rotate is acceptable for now.
    var editing by remember { mutableStateOf<WorkoutDraft?>(null) }
    var confirmDelete by remember { mutableStateOf<Workout?>(null) }

    val draft = editing
    if (draft != null) {
        WorkoutEditor(
            draft = draft,
            onChange = { editing = it },
            onCancel = { editing = null },
            onSave = { w ->
                store.update { list ->
                    if (list.any { it.id == w.id }) list.map { if (it.id == w.id) w else it } else list + w
                }
                editing = null
            },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My workouts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { editing = WorkoutDraft(id = newId()) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" New")
                }
            }
        }
        if (custom.isEmpty()) {
            item {
                Text(
                    "No workouts of your own yet. Tap New, or Copy a built-in one below and change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(custom, key = { it.id }) { w ->
            WorkoutCard(w, onClick = { onPick(w) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { editing = WorkoutDraft.from(w) }) { Text("Edit") }
                    TextButton(onClick = { editing = copyOf(w) }) { Text("Copy") }
                    TextButton(onClick = { confirmDelete = w }) { Text("Delete") }
                }
            }
        }

        item {
            Text(
                "Built-in",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        items(SampleWorkouts.all) { w ->
            WorkoutCard(w, onClick = { onPick(w) }) {
                TextButton(onClick = { editing = copyOf(w) }) { Text("Copy to My workouts") }
            }
        }
    }

    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${w.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    store.update { list -> list.filterNot { it.id == w.id } }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun newId() = UUID.randomUUID().toString()

private fun copyOf(w: Workout) = WorkoutDraft.from(w, id = newId(), name = "${w.name} (copy)")

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

// ------------------------------------------------------------------ editor

@Composable
private fun WorkoutEditor(
    draft: WorkoutDraft,
    onChange: (WorkoutDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: (Workout) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)
    val preview = draft.toWorkout()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Text(
                    "Edit workout",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { preview?.let(onSave) }, enabled = preview != null) { Text("Save") }
            }
        }
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            if (preview != null) {
                Text("Total ${formatTime(preview.totalSec)}", style = MaterialTheme.typography.bodyMedium)
                WorkoutProfile(
                    preview,
                    progress = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 4.dp),
                )
            } else {
                Text(draft.problem ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        itemsIndexed(draft.segments) { i, seg ->
            SegmentEditor(
                index = i,
                count = draft.segments.size,
                seg = seg,
                onChange = { new -> onChange(draft.update(i) { new }) },
                onMove = { delta -> onChange(draft.move(i, delta)) },
                onRemove = { onChange(draft.remove(i)) },
            )
        }
        item {
            OutlinedButton(onClick = { onChange(draft.addSegment()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Add segment")
            }
        }
    }
}

@Composable
private fun SegmentEditor(
    index: Int,
    count: Int,
    seg: SegmentDraft,
    onChange: (SegmentDraft) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
                OutlinedTextField(
                    value = seg.label,
                    onValueChange = { onChange(seg.copy(label = it)) },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(+1) }, enabled = index < count - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove, enabled = count > 1) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete segment")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(seg.minutes, "min", Modifier.width(76.dp)) { onChange(seg.copy(minutes = it)) }
                NumberField(seg.seconds, "sec", Modifier.width(76.dp)) { onChange(seg.copy(seconds = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (seg.endLevel == null) "Level" else "From",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(40.dp),
                )
                LevelStepper(seg.level) { onChange(seg.copy(level = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = seg.endLevel != null,
                    onCheckedChange = { on -> onChange(seg.copy(endLevel = if (on) seg.level else null)) },
                )
                val end = seg.endLevel
                if (end == null) {
                    Text("Ramp to another level", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("To", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                    LevelStepper(end) { onChange(seg.copy(endLevel = it)) }
                }
            }
            if (seg.durationSec == null) {
                Text("Enter a time above 0 (seconds 0–59)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LevelStepper(level: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            onClick = { onChange(level - 1) },
            enabled = level > FtmsProtocol.MIN_LEVEL,
            modifier = Modifier.size(40.dp),
        ) { Text("−", fontSize = 22.sp) }
        Text(
            "$level",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(level + 1) },
            enabled = level < FtmsProtocol.MAX_LEVEL,
            modifier = Modifier.size(40.dp),
        ) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) onChange(new) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
