package app.merachride.wear

/**
 * Messages between the phone app and the MerachRide watch app (Wear OS Data Layer, `MessageClient`).
 * Unit-tested through the phone app's identical copy.
 *
 * KEEP IN SYNC with `app/src/main/java/app/merachride/protocol/WatchProtocol.kt` (a copy, so the two
 * modules don't need a shared library module).
 */
object WatchProtocol {
    /** Watch → phone: latest heart rate, payload is the bpm as ASCII digits (e.g. "142"). */
    const val PATH_BPM = "/merachride/hr/bpm"

    /** Watch → phone: the watch stopped sharing (Stop tapped, timed out, or the workout ended). */
    const val PATH_STOPPED = "/merachride/hr/stopped"

    /** Phone → watch: keep going. The watch stops on its own if these stop arriving ([WATCH_TIMEOUT_MS]). */
    const val PATH_PING = "/merachride/hr/ping"

    /** Phone → watch: stop sharing now. */
    const val PATH_STOP = "/merachride/hr/stop"

    /** Declared by the watch app (`wear/src/main/res/values/wear.xml`), so the phone can find it. */
    const val WATCH_CAPABILITY = "merachride_hr_watch"

    /** Declared by the phone app (`app/src/main/res/values/wear.xml`), so the watch can find it. */
    const val PHONE_CAPABILITY = "merachride_phone"

    /** Opens the watch app (its activity has an intent filter for this URI). */
    const val START_URI = "merachride://heartrate/start"

    const val PING_INTERVAL_MS = 30_000L
    const val WATCH_TIMEOUT_MS = 3 * 60_000L

    fun encodeBpm(bpm: Int): ByteArray = bpm.toString().toByteArray(Charsets.US_ASCII)

    /** The bpm in a [PATH_BPM] payload, or null if it's damaged or out of range. */
    fun decodeBpm(payload: ByteArray?): Int? {
        if (payload == null || payload.isEmpty() || payload.size > 3) return null
        val bpm = String(payload, Charsets.US_ASCII).toIntOrNull() ?: return null
        return bpm.takeIf { it in 20..250 }
    }
}
