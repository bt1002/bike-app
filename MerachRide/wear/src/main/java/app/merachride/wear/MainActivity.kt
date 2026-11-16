package app.merachride.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * The watch screen: heart rate, status, and Start/Stop. Opening the app (by hand, or by the phone app
 * when the bike connects) starts sharing straight away.
 *
 * UNVERIFIED: not yet built or run on the watch.
 */
class MainActivity : ComponentActivity() {
    private val permissions = buildList {
        add(Manifest.permission.BODY_SENSORS)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasSensorPermissions()) {
                startSharing(force = false)
            } else {
                HrShare.phase.value = HrShare.Phase.ERROR
                HrShare.message.value = "Allow Sensors and Physical activity so the app can read your heart rate."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WatchScreen(
                onStart = { force -> startOrAsk(force) },
                onStop = ::stopSharing,
            )
        }
        if (savedInstanceState == null) startOrAsk(force = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startOrAsk(force = false) // Opened again, e.g. by the phone after the bike reconnected.
    }

    private fun startOrAsk(force: Boolean) {
        if (hasSensorPermissions()) startSharing(force) else requestPermissions.launch(permissions)
    }

    private fun hasSensorPermissions() = listOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION)
        .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun startSharing(force: Boolean) {
        val intent = Intent(this, HeartRateService::class.java).putExtra(HeartRateService.EXTRA_FORCE, force)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSharing() {
        startService(Intent(this, HeartRateService::class.java).setAction(HeartRateService.ACTION_STOP))
    }
}

@Composable
private fun WatchScreen(onStart: (force: Boolean) -> Unit, onStop: () -> Unit) {
    val phase by HrShare.phase.collectAsState()
    val bpm by HrShare.bpm.collectAsState()
    val message by HrShare.message.collectAsState()

    MaterialTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                bpm?.toString() ?: "--",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
            )
            Text("bpm", style = MaterialTheme.typography.caption2)
            val status = message ?: when (phase) {
                HrShare.Phase.MEASURING -> "Sending to the phone"
                HrShare.Phase.STARTING -> "Starting…"
                else -> "Not sharing"
            }
            Text(status, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
            when (phase) {
                HrShare.Phase.STARTING, HrShare.Phase.MEASURING ->
                    Chip(
                        onClick = onStop,
                        label = { Text("Stop") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                HrShare.Phase.OTHER_WORKOUT ->
                    Chip(onClick = { onStart(true) }, label = { Text("Start anyway") })
                HrShare.Phase.STOPPED, HrShare.Phase.ERROR ->
                    Chip(onClick = { onStart(false) }, label = { Text("Start") })
            }
        }
    }
}
