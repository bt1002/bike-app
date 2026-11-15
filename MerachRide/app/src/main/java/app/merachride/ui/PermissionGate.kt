package app.merachride.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Asked for alongside, but not required: without it the ride notification is just hidden. */
private val optionalPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

/** Shows [content] once the Bluetooth permissions are granted; otherwise asks for them. */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun allGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = allGranted()
        denied = !granted
    }

    if (granted) {
        content()
    } else {
        PermissionRequest(denied) { launcher.launch(requiredPermissions + optionalPermissions) }
    }
}

@Composable
private fun PermissionRequest(denied: Boolean, onRequest: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterVertically),
        ) {
            Text("Bluetooth permission needed", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Merach Ride connects to your bike over Bluetooth to read your stats and set resistance.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (denied) {
                Text(
                    "Permission was denied. If the button no longer shows a prompt, allow " +
                        "\"Nearby devices\" for Merach Ride in Android Settings → Apps.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onRequest) {
                Text("Grant permission")
            }
        }
    }
}
