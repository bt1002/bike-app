package app.merachride.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualChangeDetectorTest {

    @Test
    fun reportMatchingTargetIsNotAManualChange() {
        val d = ManualChangeDetector()
        assertNull(d.onReported(level = 8, target = 8, nowMs = 10_000))
    }

    @Test
    fun newLevelAwayFromTargetIsAManualChange() {
        val d = ManualChangeDetector()
        d.onReported(8, 8, 10_000)
        assertEquals(9, d.onReported(9, 8, 11_000))
    }

    @Test
    fun laggingReportRightAfterACommandIsIgnored() {
        val d = ManualChangeDetector()
        d.onReported(8, 8, 10_000)
        d.onCommandSent(20_000) // App asks for 16; the bike still reports an in-between value.
        assertNull(d.onReported(12, 16, 21_000))
        assertNull(d.onReported(16, 16, 22_000))
    }

    @Test
    fun changeAfterTheSettleWindowCounts() {
        val d = ManualChangeDetector(settleMs = 3_000)
        d.onCommandSent(20_000)
        d.onReported(16, 16, 21_000)
        assertEquals(15, d.onReported(15, 16, 24_000))
    }

    @Test
    fun repeatedSameReportFiresOnlyOnce() {
        val d = ManualChangeDetector()
        d.onReported(8, 8, 10_000)
        assertEquals(9, d.onReported(9, 8, 11_000))
        assertNull(d.onReported(9, 8, 12_000))
    }

    @Test
    fun unknownLevelIsIgnored() {
        val d = ManualChangeDetector()
        assertNull(d.onReported(null, 8, 10_000))
    }

    @Test
    fun firstReportWithNoTargetAdoptsTheBikeLevel() {
        val d = ManualChangeDetector()
        assertEquals(5, d.onReported(5, null, 10_000))
    }

    @Test
    fun levelUndoneOnResumeIsSentAgain() {
        // Issue #1 log: 07:37:47 app sends 10 while paused; bike reports 10, then 11 when pedalling resumes.
        val d = ManualChangeDetector(settleMs = 3_000)
        d.onReported(11, 11, 0)
        d.onCommandSent(47_000)
        d.onCommandSent(47_500) // Reply received.
        assertNull(d.onReported(10, 10, 48_000))
        assertNull(d.onReported(11, 10, 50_000)) // Resumed: within the settle window, so not a button press.
        assertFalse(d.shouldResend(10, 50_000)) // Still settling.
        assertNull(d.onReported(11, 10, 51_000))
        assertTrue(d.shouldResend(10, 51_000)) // Settled on the wrong level: send 10 again.
        assertFalse(d.shouldResend(10, 52_000)) // Only once.
    }

    @Test
    fun laggingReportThatEndsOnTargetNeedsNoResend() {
        val d = ManualChangeDetector(settleMs = 3_000)
        d.onCommandSent(20_000)
        d.onReported(12, 16, 21_000)
        d.onReported(16, 16, 22_000)
        assertFalse(d.shouldResend(16, 25_000))
    }

    @Test
    fun buttonPressAfterTheSettleWindowIsFollowedNotResent() {
        val d = ManualChangeDetector(settleMs = 3_000)
        d.onCommandSent(20_000)
        d.onReported(16, 16, 21_000)
        assertEquals(15, d.onReported(15, 16, 24_000))
        assertFalse(d.shouldResend(16, 25_000))
    }
}
