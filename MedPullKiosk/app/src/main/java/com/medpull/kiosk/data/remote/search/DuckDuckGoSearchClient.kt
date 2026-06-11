package com.medpull.kiosk.data.remote.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * DuckDuckGo search over its plain HTML endpoint (`/html/?q=…`).
 *
 * Deliberately a single HTTP GET + regex extraction: works headlessly anywhere
 * (kiosk tablet or a Wayland/no-X model host), no browser, no JS, no extra
 * parser dependency. [baseUrl] comes from BuildConfig (`SEARCH_BASE_URL` in
 * local.properties) so the public endpoint can be swapped for a self-hosted
 * instance without code changes; an HTTP (cleartext) instance additionally needs
 * its host allowed in res/xml/network_security_config.xml.
 */
class DuckDuckGoSearchClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) : WebSearchClient {

    companion object {
        private const val TAG = "DdgSearchClient"
        private const val MAX_RESULTS = 4

        // DDG's html endpoint marks each hit with result__a (title link) and
        // result__snippet. DOTALL because snippets wrap across lines.
        private val RESULT_TITLE_RX =
            Regex("""<a[^>]*class="[^"]*result__a[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        private val RESULT_SNIPPET_RX =
            Regex("""class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</(?:a|td|div|span)>""", RegexOption.DOT_MATCHES_ALL)
        private val TAG_RX = Regex("<[^>]+>")

        /** Strip tags and the handful of HTML entities DDG actually emits. */
        internal fun htmlToText(html: String): String = html
            .replace(TAG_RX, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        /** Parse DDG html-endpoint markup into results. Exposed for unit tests. */
        internal fun parseResults(html: String): List<SearchResult> {
            val titles = RESULT_TITLE_RX.findAll(html).map { htmlToText(it.groupValues[1]) }.toList()
            val snippets = RESULT_SNIPPET_RX.findAll(html).map { htmlToText(it.groupValues[1]) }.toList()
            return titles.mapIndexedNotNull { i, title ->
                if (title.isBlank()) null
                else SearchResult(title = title, snippet = snippets.getOrElse(i) { "" })
            }.take(MAX_RESULTS)
        }
    }

    override suspend fun search(query: String): SearchOutcome = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/html/?q=" + URLEncoder.encode(query, "UTF-8")
        try {
            val request = Request.Builder()
                .url(url)
                // DDG serves the no-JS page to plain clients; a UA avoids bot-walls.
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) MedPullKiosk/1.2")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Search HTTP ${response.code}")
                    return@withContext SearchOutcome.Failure("http ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val results = parseResults(body)
                if (results.isEmpty()) SearchOutcome.Failure("no results parsed")
                else SearchOutcome.Success(results)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
            SearchOutcome.Failure(e.message ?: "error")
        }
    }
}
