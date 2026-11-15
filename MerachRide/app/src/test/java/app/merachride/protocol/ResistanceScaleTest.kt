package app.merachride.protocol

import app.merachride.protocol.ResistanceScale.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResistanceScaleTest {

    @Test
    fun valueAboveMaxMeansTenths() {
        val s = ResistanceScale()
        assertEquals(8, s.toLevel(80, hint = null))
        assertEquals(Units.TENTHS, s.unit)
        assertEquals(1, s.toLevel(10, hint = null)) // the old heuristic showed 10 here
        assertEquals(16, s.toLevel(160, hint = null))
    }

    @Test
    fun smallValueMeansWholeLevels() {
        val s = ResistanceScale()
        assertEquals(8, s.toLevel(8, hint = null))
        assertEquals(Units.LEVELS, s.unit)
        assertEquals(10, s.toLevel(10, hint = null))
    }

    @Test
    fun elevenToSixteenAreWholeLevels() {
        val s = ResistanceScale()
        assertEquals(12, s.toLevel(12, hint = null))
        assertEquals(Units.LEVELS, s.unit)
    }

    @Test
    fun tenIsAmbiguousUntilLearned() {
        val s = ResistanceScale()
        assertNull(s.toLevel(10, hint = null))
        assertNull(s.toLevel(10, hint = 5))
        assertEquals(1, s.toLevel(10, hint = 1))
        assertEquals(10, s.toLevel(10, hint = 10))
        assertEquals(Units.UNKNOWN, s.unit) // a hint never fixes the unit
    }

    @Test
    fun zeroIsUnknownAndTeachesNothing() {
        val s = ResistanceScale()
        assertNull(s.toLevel(0, hint = 3))
        assertEquals(Units.UNKNOWN, s.unit)
    }

    @Test
    fun resetForgetsTheUnit() {
        val s = ResistanceScale()
        s.toLevel(80, hint = null)
        s.reset()
        assertEquals(Units.UNKNOWN, s.unit)
    }
}
