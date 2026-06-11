package com.medpull.kiosk.data.remote.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckDuckGoSearchClientTest {

    private val sampleHtml = """
        <div class="result results_links results_links_deep web-result ">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="https://example.com/a">Where is the <b>group number</b> on an insurance card?</a>
          </h2>
          <a class="result__snippet" href="https://example.com/a">The <b>group number</b> is usually printed
            below the member ID on the front of the card.</a>
        </div>
        <div class="result">
          <a class="result__a" href="https://example.com/b">Insurance card basics</a>
          <td class="result__snippet">Member ID identifies you; the group number identifies your employer&#x27;s plan.</td>
        </div>
    """.trimIndent()

    @Test
    fun `parses titles and snippets from ddg html`() {
        val results = DuckDuckGoSearchClient.parseResults(sampleHtml)
        assertEquals(2, results.size)
        assertEquals("Where is the group number on an insurance card?", results[0].title)
        assertTrue(results[0].snippet.contains("below the member ID"))
        assertTrue(results[1].snippet.contains("employer's plan"))
    }

    @Test
    fun `entities and tags are stripped`() {
        assertEquals(
            "Tom & Jerry's \"plan\"",
            DuckDuckGoSearchClient.htmlToText("<b>Tom</b> &amp; Jerry&#x27;s &quot;plan&quot;")
        )
    }

    @Test
    fun `empty page parses to no results`() {
        assertTrue(DuckDuckGoSearchClient.parseResults("<html><body>no results here</body></html>").isEmpty())
    }
}
