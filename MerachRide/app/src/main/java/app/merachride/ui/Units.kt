package app.merachride.ui

/**
 * Display units. Everything is stored and calculated in metric (that's what the bike reports and what
 * rides.json holds); these helpers convert to imperial only when showing values.
 */
object Units {
    private const val METERS_PER_MILE = 1609.344
    private const val KMH_PER_MPH = 1.609344

    const val SPEED = "mph"
    const val DISTANCE = "mi"

    /** e.g. "17.6" (mph). */
    fun speed(kmh: Double): String = "%.1f".format(kmh / KMH_PER_MPH)

    /** e.g. "3.12" (miles). */
    fun distance(meters: Int): String = "%.2f".format(meters / METERS_PER_MILE)

    /** e.g. "124.5" (miles), for totals. */
    fun distanceShort(meters: Int): String = "%.1f".format(meters / METERS_PER_MILE)
}
