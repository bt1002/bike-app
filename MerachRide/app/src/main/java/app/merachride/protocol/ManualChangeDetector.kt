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
    /** True if [lastReported] first arrived while an app command was settling. */
    private var arrivedWhileSettling = false

    fun reset() {
        lastCommandAtMs = null
        lastReported = null
        arrivedWhileSettling = false
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
        if (sentAt != null && nowMs - sentAt < settleMs) {
            arrivedWhileSettling = true
            return null
        }
        arrivedWhileSettling = false
        return level.takeIf { it != target }
    }

    /**
     * True (once) when the bike has settled on a level other than [target], and that level arrived while
     * an app command was settling, so it isn't a button press: the bike didn't keep the commanded level.
     * Seen on the S29 (issue #1 log, 2026-11-16): a level sent while the rider had stopped pedalling was
     * reported, then undone when they started again. The caller should send the target again.
     * A button press within [settleMs] of an app command is also caught here and overridden; pressing
     * again after that is followed as usual.
     */
    fun shouldResend(target: Int?, nowMs: Long): Boolean {
        val level = lastReported
        if (!arrivedWhileSettling || level == null || target == null) return false
        val sentAt = lastCommandAtMs
        if (sentAt != null && nowMs - sentAt < settleMs) return false
        arrivedWhileSettling = false
        return level != target
    }
}
