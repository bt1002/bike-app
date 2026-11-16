package app.merachride.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.merachride.BikeSession
import app.merachride.ConnState
import app.merachride.report.IssueReport

/**
 * Asks what went wrong, then opens GitHub's "new issue" page with the description, app details and the
 * connection log filled in. The rider submits it from their browser. UNVERIFIED: not yet built.
 */
@Composable
fun ReportIssueDialog(session: BikeSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report an issue") },
        text = {
            Column {
                Text(
                    "Say what happened and what you expected. The connection log is attached. GitHub opens " +
                        "with the issue filled in; check it and press Create.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. Level jumped to 16 during Quick Test") },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openIssue(context, session, text)
                onDismiss()
            }) { Text("Open GitHub") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun openIssue(context: Context, session: BikeSession, description: String) {
    val report = IssueReport.build(description, details(context, session), session.logLines.value)
    if (report.truncated) {
        // The link can't hold the whole log; put everything on the clipboard to paste into the issue.
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("MerachRide report", report.fullText))
        Toast.makeText(
            context,
            "Log too long for the link: the newest ${report.logLinesIncluded} of ${report.logLinesTotal} lines are " +
                "attached, and the full report is on the clipboard.",
            Toast.LENGTH_LONG,
        ).show()
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.url)))
        session.log("Opened a GitHub issue report (${report.logLinesIncluded} log lines)")
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open GitHub.", Toast.LENGTH_LONG).show()
    }
}

/** Short facts that help with a bug report. No personal data beyond the phone model. */
private fun details(context: Context, session: BikeSession): List<String> {
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        null
    }
    val bike = when (val s = session.state.value) {
        ConnState.Idle -> "not connected"
        is ConnState.Connecting -> "connecting (attempt ${s.attempt})"
        is ConnState.Ready -> "connected to ${s.name ?: "bike"}"
    }
    val ws = session.workout.state.value
    val workout = ws.workout?.let { w ->
        val status = when {
            ws.finished -> "finished"
            ws.running -> "running"
            else -> "paused"
        }
        val offset = if (ws.levelOffset != 0) ", levels %+d".format(ws.levelOffset) else ""
        val time = "%d:%02d".format(ws.elapsedSec / 60, ws.elapsedSec % 60)
        "${w.name}, $status at $time$offset" // No format() here: a workout name may contain '%'.
    } ?: "none"
    return listOf(
        "App: ${version ?: "?"}",
        "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Bike: $bike, target level ${session.targetLevel.value ?: "?"}",
        "Workout: $workout",
    )
}
