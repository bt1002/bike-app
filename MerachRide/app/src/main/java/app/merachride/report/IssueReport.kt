package app.merachride.report

import java.net.URLEncoder

/**
 * Builds a "new GitHub issue" link for the repo, pre-filled with the rider's description, some app
 * details and the connection log. The rider submits it in their browser (signed in to GitHub), so the
 * app holds no GitHub token. Pure Kotlin, unit-tested.
 *
 * Browsers and GitHub cope badly with very long links, so the oldest log lines are dropped until the
 * link fits [MAX_URL_LENGTH]; [Report.truncated] tells the UI to put the full text on the clipboard.
 */
object IssueReport {
    const val REPO_URL = "https://github.com/bt1002/bike-app"
    const val MAX_URL_LENGTH = 7_500
    private const val MAX_TITLE = 80

    data class Report(
        /** Link to open in the browser. */
        val url: String,
        /** The whole report with every log line, for the clipboard. */
        val fullText: String,
        val logLinesIncluded: Int,
        val logLinesTotal: Int,
    ) {
        val truncated: Boolean get() = logLinesIncluded < logLinesTotal
    }

    /**
     * @param details short "Key: value" lines (app version, phone, bike state…).
     * @param logNewestFirst the connection log as the app keeps it (newest first).
     */
    fun build(
        description: String,
        details: List<String>,
        logNewestFirst: List<String>,
        maxUrlLength: Int = MAX_URL_LENGTH,
    ): Report {
        val log = logNewestFirst.reversed() // Oldest first reads better in an issue.
        val title = title(description)
        val fullText = body(description, details, log, omitted = 0)

        var desc = description.trim()
        var keep = log.size
        while (true) {
            val url = url(title, body(desc, details, log.takeLast(keep), omitted = log.size - keep))
            if (url.length <= maxUrlLength) return Report(url, fullText, keep, log.size)
            when {
                keep > 0 -> keep--
                desc.length > 200 -> desc = desc.take(desc.length / 2).trimEnd() + "… (cut; full text on the clipboard)"
                else -> return Report(url, fullText, 0, log.size) // Can't shrink further; let GitHub cope.
            }
        }
    }

    /** First non-blank line of the description, shortened, or a stock title. */
    fun title(description: String): String {
        val first = description.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return "Problem report from the app"
        return if (first.length <= MAX_TITLE) first else first.take(MAX_TITLE - 1).trimEnd() + "…"
    }

    fun body(description: String, details: List<String>, logOldestFirst: List<String>, omitted: Int): String =
        buildString {
            appendLine(description.trim().ifEmpty { "_(no description)_" })
            appendLine()
            if (details.isNotEmpty()) {
                appendLine("**Details**")
                details.forEach { appendLine("- $it") }
                appendLine()
            }
            val shown = logOldestFirst.size
            appendLine("<details><summary>Connection log ($shown lines${if (omitted > 0) ", $omitted older left out" else ""})</summary>")
            appendLine()
            appendLine("```text")
            if (omitted > 0) appendLine("… $omitted older lines left out to fit the link (the app copied the full log to the clipboard)")
            logOldestFirst.forEach(::appendLine)
            if (logOldestFirst.isEmpty() && omitted == 0) appendLine("(log is empty)")
            appendLine("```")
            appendLine("</details>")
            appendLine()
            append("_Sent from the app's Report issue button._")
        }

    fun url(title: String, body: String): String =
        "$REPO_URL/issues/new?labels=bug&title=${encode(title)}&body=${encode(body)}"

    /** Percent-encoding for a query value, with spaces as %20 rather than '+'. */
    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
