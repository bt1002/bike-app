package app.merachride.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class IssueReportTest {
    private val log = (1..100).map { "10:00:%02d  line $it".format(it % 60) }.reversed() // Newest first, like the app.

    private fun bodyOf(url: String): String =
        URLDecoder.decode(url.substringAfter("&body="), "UTF-8")

    @Test
    fun titleIsTheFirstLineShortened() {
        assertEquals("Level jumped to 16", IssueReport.title("\n  Level jumped to 16  \nduring Quick Test"))
        assertEquals("Problem report from the app", IssueReport.title("   "))
        val long = "x".repeat(200)
        assertEquals(80, IssueReport.title(long).length)
        assertTrue(IssueReport.title(long).endsWith("…"))
    }

    @Test
    fun shortLogFitsWhole() {
        val r = IssueReport.build("It broke", listOf("App: 0.1.0"), log.take(5))
        assertFalse(r.truncated)
        val body = bodyOf(r.url)
        assertTrue(body.startsWith("It broke"))
        assertTrue(body.contains("- App: 0.1.0"))
        // Oldest first: line 96 comes before line 100.
        assertTrue(body.indexOf("line 96") < body.indexOf("line 100"))
        assertTrue(r.url.startsWith("https://github.com/bt1002/bike-app/issues/new?labels=bug&title=It%20broke&body="))
    }

    @Test
    fun longLogDropsOldestLinesToFit() {
        val r = IssueReport.build("Hi", emptyList(), log, maxUrlLength = 2_000)
        assertTrue(r.url.length <= 2_000)
        assertTrue(r.truncated)
        assertEquals(100, r.logLinesTotal)
        val body = bodyOf(r.url)
        assertTrue(body.contains("line 100")) // Newest kept.
        assertFalse(body.contains("line 1\n")) // Oldest dropped.
        assertTrue(body.contains("older lines left out"))
        // The clipboard text has everything.
        assertTrue(r.fullText.contains("line 1\n"))
        assertTrue(r.fullText.contains("line 100"))
    }

    @Test
    fun hugeDescriptionIsCutToFit() {
        val r = IssueReport.build("word ".repeat(2_000), emptyList(), log, maxUrlLength = 3_000)
        assertTrue(r.url.length <= 3_000)
        assertEquals(0, r.logLinesIncluded)
    }

    @Test
    fun encodingRoundTripsSpecialCharacters() {
        val s = "a b+c&d=e#f → ✓\nnew line"
        val enc = IssueReport.encode(s)
        assertFalse(enc.contains(" "))
        assertFalse(enc.contains("+"))
        assertEquals(s, URLDecoder.decode(enc, "UTF-8"))
    }

    @Test
    fun emptyLogSaysSo() {
        val r = IssueReport.build("", emptyList(), emptyList())
        assertTrue(bodyOf(r.url).contains("(log is empty)"))
        assertTrue(bodyOf(r.url).contains("_(no description)_"))
    }
}
