package app.merachride.wear

import kotlinx.coroutines.flow.MutableStateFlow

/** What [HeartRateService] is doing, for the watch screen. Updated on the main thread. */
object HrShare {
    enum class Phase { STOPPED, STARTING, MEASURING, OTHER_WORKOUT, ERROR }

    val phase = MutableStateFlow(Phase.STOPPED)
    val bpm = MutableStateFlow<Int?>(null)
    /** A line for the user (what's happening, or why it stopped), or null. */
    val message = MutableStateFlow<String?>(null)
}
