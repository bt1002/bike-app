package app.merachride.protocol

/**
 * Spots resistance changes made with the bike's own buttons, so the app's target can follow them.
 * UNVERIFIED on the S29: we don't yet know whether the bike reports button changes in 0x2AD2.
 *
 * A newly reported level that differs from the app's target counts as a change on the bike,
 * except within [settleMs] of an app command, because the bike's report lags behind a command
 * and would otherwise look like a manual change.
 */
class ManualChangeDetector(private val settleMs: Long = 3_000L) {
    private var lastCommandAtMs: Long? = null
    private var lastReported: Int? = null

    fun reset() {
        lastCommandAtMs = null
        lastReported = null
    }

    /** Call whenever the app sends a Control Point command (and again when it completes). */
    fun onCommandSent(nowMs: Long) {
        lastCommandAtMs = nowMs
    }

    /** Returns the level the rider set on the bike, or null if this report isn't a manual change. */
    fun onReported(level: Int?, target: Int?, nowMs: Long): Int? {
        val previous = lastReported
        lastReported = level
        if (level == null || level == previous) return null
        val sentAt = lastCommandAtMs
        if (sentAt != null && nowMs - sentAt < settleMs) return null
        return level.takeIf { it != target }
    }
}
