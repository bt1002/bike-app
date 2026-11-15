package app.merachride

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.merachride.ui.MerachRideTheme
import app.merachride.ui.PermissionGate
import app.merachride.ui.RideScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen on while the app is open, so the ride stats stay visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val session = (application as MerachRideApp).session
        setContent {
            MerachRideTheme {
                PermissionGate {
                    RideScreen(session)
                }
            }
        }
    }
}
