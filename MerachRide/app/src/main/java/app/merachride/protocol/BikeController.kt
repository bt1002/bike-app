package app.merachride.protocol

import kotlinx.coroutines.flow.StateFlow

/**
 * What the workout engine needs from a bike. FTMS is the only implementation today;
 * a Merach-proprietary one could be added behind this interface if ever needed.
 */
interface BikeController {
    val data: StateFlow<BikeData>

    /** Sets resistance (1..16). If disconnected, the level is remembered and applied on reconnect. */
    suspend fun setResistanceLevel(level: Int): Boolean
}
