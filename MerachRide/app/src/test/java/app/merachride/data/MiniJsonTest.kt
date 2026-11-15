package app.merachride.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniJsonTest {

    @Test
    fun roundTripsNestedValues() {
        val value = mapOf(
            "name" to "Hill \"climb\" \\ 2\nline",
            "n" to 42L,
            "x" to 1.5,
            "ok" to true,
            "none" to null,
            "list" to listOf(1L, "two", listOf<Any?>(), mapOf<String, Any?>()),
        )
        assertEquals(value, MiniJson.parse(MiniJson.write(value)))
    }

    @Test
    fun parsesWhitespaceAndUnicodeEscapes() {
        val parsed = MiniJson.parse(" { \"a\" : [ 1 , -2.5e1 ] , \"b\" : \"\\u00e9\" } ").asObject()
        assertEquals(listOf(1L, -25.0), parsed["a"])
        assertEquals("é", parsed["b"])
    }

    @Test
    fun writesIntsWithoutDecimals() {
        assertEquals("{\"a\":3}", MiniJson.write(mapOf("a" to 3)))
    }

    @Test
    fun parsesNull() {
        assertNull(MiniJson.parse("null"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedInput() {
        MiniJson.parse("{\"a\":[1,2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTrailingGarbage() {
        MiniJson.parse("{} x")
    }
}
