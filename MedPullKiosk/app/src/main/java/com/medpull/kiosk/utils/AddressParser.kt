package com.medpull.kiosk.utils

import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FieldUpdate
import com.medpull.kiosk.data.models.FormField

/**
 * Deterministic US address parsing for the intake flow.
 *
 * When the patient gives a full address in one go ("123 Main St, Houston, TX
 * 77001" typed, or "123 main street houston texas 77001" spoken), the app
 * splits it into street / city / state / ZIP itself, fills the sibling fields,
 * and asks ONE "Is this correct?" confirmation instead of re-asking city,
 * state, and ZIP one by one. No AI involved.
 *
 * Handles every schema naming style in assets/schemas:
 *   address_street + address_city/_state/_zip          (sliding fee)
 *   mailing_address_street + mailing_city/_state/_zip  (coastal gateway)
 *   mailing_address + city/state/zip                   (patient registration)
 *   parent1_mailing_address + parent1_city_state_zip   (combined sibling)
 */
object AddressParser {

    data class Parsed(
        val street: String,
        val city: String? = null,
        val state: String? = null,
        val zip: String? = null
    )

    data class Related(
        val cityField: FormField? = null,
        val stateField: FormField? = null,
        val zipField: FormField? = null,
        /** A combined "City / State / ZIP" sibling (e.g. parent1_city_state_zip). */
        val combinedField: FormField? = null
    )

    // Common street-type suffixes used to find the street/city boundary in
    // comma-less (spoken) addresses.
    private val STREET_SUFFIXES = setOf(
        "st", "street", "ave", "avenue", "rd", "road", "dr", "drive", "ln", "lane",
        "blvd", "boulevard", "ct", "court", "cir", "circle", "way", "pl", "place",
        "trl", "trail", "pkwy", "parkway", "hwy", "highway", "ter", "terrace",
        "loop", "run", "pike", "row", "walk", "plaza", "sq", "square", "bend",
        "xing", "crossing", "cv", "cove", "pt", "point"
    )
    private val UNIT_MARKERS = setOf("apt", "apartment", "unit", "suite", "ste", "#", "no")

    private val ZIP_RX = Regex("""^(\d{5})(?:-\d{4})?$""")

    /** Street-address fields across all schema naming styles. */
    fun isStreetField(field: FormField): Boolean {
        if (field.fieldType != FieldType.TEXT || field.options.isNotEmpty()) return false
        val id = field.id.lowercase()
        val excluded = listOf("city", "state", "zip", "county", "email", "same_as", "confirmation", "apt")
        if (excluded.any { id.contains(it) }) return false
        return id.endsWith("_street") || id == "street" || id.contains("address")
    }

    /**
     * Find the still-blank sibling fields a parsed address can fill, by trying
     * progressively shorter prefixes of the street field's id (the generalized
     * version of the engine's old findRelated logic).
     */
    fun findRelated(streetField: FormField, allFields: List<FormField>): Related {
        val tokens = streetField.id.lowercase().split("_")
        val idParts = if (tokens.last() in setOf("street", "address")) tokens.dropLast(1) else tokens

        fun find(keyword: String, exclude: List<String> = emptyList()): FormField? {
            for (len in idParts.size downTo 0) {
                val prefix = if (len == 0) "" else idParts.take(len).joinToString("_") + "_"
                val match = allFields.find { f ->
                    val fid = f.id.lowercase()
                    f.id != streetField.id && f.value.isNullOrBlank() &&
                        f.fieldType != FieldType.STATIC_LABEL &&
                        exclude.none { fid.contains(it) } &&
                        (if (prefix.isEmpty()) fid == keyword
                         else fid.startsWith(prefix) && fid.endsWith(keyword))
                }
                if (match != null) return match
            }
            return null
        }

        val combined = find("city_state_zip")
        return Related(
            cityField = if (combined == null) find("city", exclude = listOf("state_zip")) else null,
            stateField = if (combined == null) find("state", exclude = listOf("city_state_zip", "same_as")) else null,
            zipField = if (combined == null) find("zip", exclude = listOf("city_state_zip")) else null,
            combinedField = combined
        )
    }

    /**
     * Parse a free-form answer into address parts. Returns null when the text
     * doesn't look like it carries more than a street (then the normal one-field
     * flow continues). City/state/zip are null when that part wasn't found.
     */
    fun parse(raw: String): Parsed? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.length < 5) return null
        val parsed = if (text.contains(",")) parseWithCommas(text) else parseWithoutCommas(text)
        return parsed?.takeIf { it.city != null || it.state != null || it.zip != null }
            ?.normalized()
    }

    private fun Parsed.normalized() = Parsed(
        street = titleCase(street.trimEnd(',', '.')),
        city = city?.let { titleCase(it.trimEnd(',', '.')) },
        state = state?.let { normalizeState(it) },
        zip = zip?.let { ZIP_RX.find(it)?.groupValues?.get(1) }
    )

    /** "Houston, TX 77001"-style display text for the confirm panel / spoken question. */
    fun displayText(parsed: Parsed): String {
        val tail = listOfNotNull(
            parsed.city,
            listOfNotNull(parsed.state, parsed.zip).joinToString(" ").ifBlank { null }
        ).joinToString(", ")
        return if (tail.isBlank()) parsed.street else "${parsed.street}, $tail"
    }

    // ─── comma form: "123 Main St, Houston, TX 77001" / "123 Main St, Houston TX 77001" ──

    private fun parseWithCommas(text: String): Parsed? {
        val parts = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val street = parts[0]
        // A street has a house/box number; this keeps conversational answers
        // that merely contain a comma ("no, I don't have one") out of the parser.
        if (!street.any { it.isDigit() }) return null
        // Everything after the first comma is the city/state/zip tail, however
        // the patient distributed their commas.
        val tailTokens = parts.drop(1).flatMap { it.split(" ") }.filter { it.isNotBlank() }
        val (city, state, zip) = splitTail(tailTokens)
        return Parsed(street, city, state, zip)
    }

    // ─── spoken form: "123 main street houston texas 77001" ───────────────────

    private fun parseWithoutCommas(text: String): Parsed? {
        val tokens = text.split(" ").filter { it.isNotBlank() }
        if (tokens.size < 4) return null
        // Must start like a street (leading house number) to avoid mangling
        // non-address answers.
        if (!tokens.first().any { it.isDigit() }) return null

        // Locate the end of the street: the LAST street-suffix token.
        var streetEnd = -1
        tokens.forEachIndexed { i, t ->
            if (clean(t) in STREET_SUFFIXES) streetEnd = i
        }
        if (streetEnd == -1) {
            // No recognizable suffix → the street/city boundary is unknowable.
            // Accept only the "street + state/zip" shape and leave city unset;
            // a stray city would surface in the confirm step and get a "No".
            val (city, state, zip) = splitTail(tokens)
            if (state == null && zip == null) return null
            val street = (city ?: "").ifBlank { return null }
            return Parsed(street, null, state, zip)
        }

        // Absorb unit tokens right after the suffix ("blvd apt 3b" / "blvd 3b").
        var i = streetEnd + 1
        while (i < tokens.size) {
            val t = clean(tokens[i])
            val unitValueRx = Regex("""^#?\d+[a-z]{0,2}$""")
            val follows = clean(tokens[i - 1]) in UNIT_MARKERS
            if (t in UNIT_MARKERS || (unitValueRx.matches(t) && !ZIP_RX.matches(t)) || (follows && t.isNotBlank())) {
                streetEnd = i; i++
            } else break
        }

        val rest = tokens.drop(streetEnd + 1)
        if (rest.isEmpty()) return Parsed(tokens.joinToString(" "))
        val (city, state, zip) = splitTail(rest)
        return Parsed(
            street = tokens.take(streetEnd + 1).joinToString(" "),
            city = city,
            state = state,
            zip = zip
        )
    }

    /**
     * Pull (city, state, zip) off the END of a token list: optional zip last,
     * then a 2-letter code or full state name (1–3 words), remaining tokens are
     * the city.
     */
    private fun splitTail(tokens: List<String>): Triple<String?, String?, String?> {
        if (tokens.isEmpty()) return Triple(null, null, null)
        var rest = tokens
        var zip: String? = null
        if (ZIP_RX.matches(clean(rest.last()))) {
            zip = clean(rest.last())
            rest = rest.dropLast(1)
        }
        var state: String? = null
        if (rest.isNotEmpty()) {
            // Try 3-, 2-, then 1-word state names, then a bare 2-letter code.
            for (n in minOf(3, rest.size) downTo 1) {
                val candidate = rest.takeLast(n).joinToString(" ") { clean(it) }
                val byName = FieldValidation.STATE_NAME_TO_CODE[candidate]
                val byCode = if (n == 1 && candidate.length == 2 &&
                    candidate.uppercase() in FieldValidation.US_STATES
                ) candidate.uppercase() else null
                val code = byName ?: byCode
                if (code != null) {
                    state = code
                    rest = rest.dropLast(n)
                    break
                }
            }
        }
        val city = rest.joinToString(" ").ifBlank { null }
        return Triple(city, state, zip)
    }

    private fun clean(token: String) = token.trim().trimEnd(',', '.').lowercase()

    private fun normalizeState(raw: String): String? {
        val t = raw.trim().trimEnd(',', '.')
        if (t.length == 2 && t.uppercase() in FieldValidation.US_STATES) return t.uppercase()
        return FieldValidation.STATE_NAME_TO_CODE[t.lowercase()]
    }

    private fun titleCase(s: String): String = s.split(" ").joinToString(" ") { word ->
        when {
            word.isEmpty() -> word
            // Keep things like "3B", "P.O.", "#12" as typed
            word.any { it.isDigit() } || word.length <= 1 -> word.uppercase()
            else -> word[0].uppercase() + word.substring(1).lowercase()
        }
    }

    /**
     * Build the sibling-field updates for a parsed address. Returns an empty
     * list when there is nothing (blank) to fill — caller then proceeds with
     * the normal single-field flow.
     */
    fun fillsFor(parsed: Parsed, related: Related): List<FieldUpdate> = buildList {
        val combined = related.combinedField
        if (combined != null) {
            val tail = listOfNotNull(
                parsed.city,
                listOfNotNull(parsed.state, parsed.zip).joinToString(" ").ifBlank { null }
            ).joinToString(", ")
            if (tail.isNotBlank()) add(FieldUpdate(combined.id, tail, 1.0f))
            return@buildList
        }
        parsed.city?.let { c -> related.cityField?.let { add(FieldUpdate(it.id, c, 1.0f)) } }
        parsed.state?.let { s -> related.stateField?.let { add(FieldUpdate(it.id, s, 1.0f)) } }
        parsed.zip?.let { z -> related.zipField?.let { add(FieldUpdate(it.id, z, 1.0f)) } }
    }
}
