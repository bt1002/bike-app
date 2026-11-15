package app.merachride.history

import app.merachride.data.MiniJson
import app.merachride.data.asObject
import app.merachride.data.int
import app.merachride.data.intOrNull
import app.merachride.data.long
import app.merachride.data.objects
import app.merachride.data.str
import app.merachride.data.strOrNull

/** Save format for ride history (`rides.json`). */
object RideCodec {
    private const val VERSION = 1

    fun encode(rides: List<RideRecord>): String = MiniJson.write(
        mapOf(
            "version" to VERSION,
            "rides" to rides.map { r ->
                mapOf(
                    "id" to r.id,
                    "start" to r.startEpochMs,
                    "sec" to r.durationSec,
                    "movingSec" to r.movingSec,
                    "distanceM" to r.distanceM,
                    "kcal" to r.energyKcal,
                    "avgW" to r.avgPowerW,
                    "maxW" to r.maxPowerW,
                    "avgRpm" to r.avgCadenceRpm,
                    "avgHr" to r.avgHeartRateBpm,
                    "maxHr" to r.maxHeartRateBpm,
                    "workout" to r.workoutName,
                )
            },
        ),
    )

    /** Reads rides back. A damaged entry is skipped rather than losing the whole history. */
    fun decode(text: String): List<RideRecord> =
        MiniJson.parse(text).asObject().objects("rides").mapNotNull { r ->
            runCatching {
                RideRecord(
                    id = r.str("id"),
                    startEpochMs = r.long("start"),
                    durationSec = r.int("sec"),
                    movingSec = r.intOrNull("movingSec") ?: 0,
                    distanceM = r.intOrNull("distanceM") ?: 0,
                    energyKcal = r.intOrNull("kcal") ?: 0,
                    avgPowerW = r.intOrNull("avgW"),
                    maxPowerW = r.intOrNull("maxW"),
                    avgCadenceRpm = r.intOrNull("avgRpm"),
                    avgHeartRateBpm = r.intOrNull("avgHr"),
                    maxHeartRateBpm = r.intOrNull("maxHr"),
                    workoutName = r.strOrNull("workout"),
                )
            }.getOrNull()
        }
}
