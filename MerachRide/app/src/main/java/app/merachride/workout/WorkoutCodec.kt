package app.merachride.workout

import app.merachride.data.MiniJson
import app.merachride.data.asObject
import app.merachride.data.int
import app.merachride.data.objects
import app.merachride.data.str
import app.merachride.data.strOrNull
import app.merachride.protocol.FtmsProtocol

/** Save format for custom workouts (`workouts.json`). */
object WorkoutCodec {
    private const val VERSION = 1

    fun encode(workouts: List<Workout>): String = MiniJson.write(
        mapOf(
            "version" to VERSION,
            "workouts" to workouts.map { w ->
                mapOf(
                    "id" to w.id,
                    "name" to w.name,
                    "segments" to w.segments.map { s ->
                        mapOf("sec" to s.durationSec, "level" to s.level, "label" to s.label)
                    },
                )
            },
        ),
    )

    /** Reads workouts back. A damaged entry is skipped rather than losing the whole file. */
    fun decode(text: String): List<Workout> {
        val root = MiniJson.parse(text).asObject()
        return root.objects("workouts").mapNotNull { w ->
            runCatching {
                Workout(
                    name = w.str("name"),
                    segments = w.objects("segments").map { s ->
                        Segment(
                            durationSec = s.int("sec"),
                            level = s.int("level").coerceIn(FtmsProtocol.MIN_LEVEL, FtmsProtocol.MAX_LEVEL),
                            label = s.strOrNull("label") ?: "",
                        )
                    },
                    id = w.str("id"),
                )
            }.getOrNull()
        }
    }
}
