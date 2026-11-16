package app.merachride.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.merachride.MerachRideApp

private enum class Tab(val label: String, val icon: ImageVector) {
    Ride("Ride", Icons.Filled.PlayArrow),
    Workouts("Workouts", Icons.AutoMirrored.Filled.List),
    History("History", Icons.Filled.DateRange),
}

/** App shell: bottom navigation between the ride, workout and history screens. */
@Composable
fun MainScreen(app: MerachRideApp) {
    var tab by rememberSaveable { mutableStateOf(Tab.Ride) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            Tab.Ride -> RideScreen(
                session = app.session,
                tracker = app.rideTracker,
                heartRate = app.heartRate,
                watchLink = app.watchLink,
                onChooseWorkout = { tab = Tab.Workouts },
                modifier = m,
            )
            Tab.Workouts -> WorkoutsScreen(
                store = app.customWorkouts,
                onPick = { w ->
                    app.session.workout.load(w)
                    tab = Tab.Ride
                },
                modifier = m,
            )
            Tab.History -> HistoryScreen(app.rideHistory, modifier = m)
        }
    }
}
