package app.merachride.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
