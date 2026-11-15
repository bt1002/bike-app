package app.merachride.workout

import app.merachride.data.MiniJson
import app.merachride.data.asObject

/**
 * Merges workouts from an imported `workouts.json` (from the web builder or another phone) into
 * the user's own workouts. A workout with the same id replaces the existing one; others are added.
 */
object WorkoutImport {

    data class Result(val workouts: List<Workout>, val added: Int, val updated: Int, val skipped: Int) {
        /** e.g. "Imported: added 2, updated 1." */
        val summary: String
            get() {
                val parts = buildList {
                    if (added > 0) add("added $added")
                    if (updated > 0) add("updated $updated")
                    if (skipped > 0) add("skipped $skipped damaged")
                }
                return if (added + updated == 0 && skipped == 0) {
                    "That file has no workouts in it."
                } else {
                    "Imported: ${parts.joinToString(", ")}."
                }
            }
    }

    /**
     * Parses [text] and merges it into [existing]. Throws [IllegalArgumentException] if [text]
     * isn't a workouts file at all. [newId] supplies ids for entries that arrive without one.
     */
    fun merge(existing: List<Workout>, text: String, newId: () -> String): Result {
        val total = (MiniJson.parse(text).asObject()["workouts"] as? List<*>)?.size
            ?: throw IllegalArgumentException("This file has no \"workouts\" list.")
        val incoming = WorkoutCodec.decode(text).map { if (it.id.isBlank()) it.copy(id = newId()) else it }

        val result = existing.toMutableList()
        var added = 0
        var updated = 0
        for (w in incoming) {
            val i = result.indexOfFirst { it.id == w.id }
            if (i >= 0) {
                result[i] = w
                updated++
            } else {
                result += w
                added++
            }
        }
        return Result(result, added, updated, skipped = total - incoming.size)
    }
}
