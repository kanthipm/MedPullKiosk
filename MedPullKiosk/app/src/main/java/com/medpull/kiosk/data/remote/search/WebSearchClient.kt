package com.medpull.kiosk.data.remote.search

/**
 * Pluggable web-lookup used by the intake co-pilot when the model flags
 * `needs_search` (a factual patient question it can't answer from training).
 *
 * Implementations MUST be plain HTTP — no WebView, no browser automation, no
 * display-server dependency — so the same lookup path works from the tablet and
 * from any headless host. Queries are composed by the model under a strict
 * no-patient-details rule and re-screened by the caller; never log them with PHI
 * assumptions anyway.
 */
interface WebSearchClient {
    suspend fun search(query: String): SearchOutcome
}

/** One search hit, trimmed to what the answer model needs. */
data class SearchResult(
    val title: String,
    val snippet: String
)

sealed class SearchOutcome {
    data class Success(val results: List<SearchResult>) : SearchOutcome()
    /** Lookup unavailable or failed — caller degrades to the model's own answer. */
    data class Failure(val reason: String) : SearchOutcome()
}
