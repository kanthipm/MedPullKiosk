package com.medpull.kiosk.utils

import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField

/**
 * Lightweight, format-level validation for free-text intake answers.
 *
 * The conversation engine decides *what* a patient meant; this decides whether
 * the result is a sensible value for the field (a real phone number, a valid
 * email, a 5-digit ZIP, a parseable date, …). It also normalizes valid values
 * to a consistent format so the filled PDF looks clean.
 *
 * Only TEXT / NUMBER / DATE fields are checked — options-based fields (radio,
 * dropdown, multi-select), checkboxes, signatures and static labels are already
 * constrained, so they pass through untouched.
 *
 * The semantic kind is inferred from the field id (our schemas name fields
 * consistently: phone, email, zip, ssn, state, date_of_birth / dob, and name)
 * plus the field type, so no schema changes are required.
 */
object FieldValidation {

    enum class Kind { NAME, PHONE, EMAIL, ZIP, SSN, DATE, STATE, NUMBER, TEXT }

    /**
     * @param ok         whether [normalized] is an acceptable value
     * @param normalized cleaned-up value to store when [ok] is true
     * @param hintRes    a localized "that doesn't look right" message when [ok] is false
     */
    data class Result(val ok: Boolean, val normalized: String, val hintRes: Int? = null)

    internal val US_STATES = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN",
        "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV",
        "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN",
        "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY", "DC", "PR", "VI", "GU", "AS", "MP"
    )
    internal val STATE_NAME_TO_CODE = mapOf(
        "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
        "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
        "florida" to "FL", "georgia" to "GA", "hawaii" to "HI", "idaho" to "ID",
        "illinois" to "IL", "indiana" to "IN", "iowa" to "IA", "kansas" to "KS",
        "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME", "maryland" to "MD",
        "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN", "mississippi" to "MS",
        "missouri" to "MO", "montana" to "MT", "nebraska" to "NE", "nevada" to "NV",
        "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM", "new york" to "NY",
        "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH", "oklahoma" to "OK",
        "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI", "south carolina" to "SC",
        "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX", "utah" to "UT",
        "vermont" to "VT", "virginia" to "VA", "washington" to "WA", "west virginia" to "WV",
        "wisconsin" to "WI", "wyoming" to "WY", "district of columbia" to "DC", "puerto rico" to "PR"
    )

    // Decline / "I don't have one" words across all supported languages. Only applied
    // to free-text fields (see the caller), so single words like "no"/"non"/"нет" can't
    // hijack a Yes/No radio answer.
    private val DECLINE_EXACT = setOf(
        // English
        "none", "no", "n/a", "na", "skip", "skipped", "decline", "declined", "nope",
        "-", "—", "x", "nil", "null",
        // Spanish
        "ninguno", "ninguna", "nada", "omitir", "saltar",
        // French
        "aucun", "aucune", "non", "passer",
        // Portuguese
        "nenhum", "nenhuma", "não", "nao", "pular",
        // Russian
        "нет", "нету", "отсутствует", "пропустить"
    )
    private val DECLINE_CONTAINS = listOf(
        // English
        "don't have", "dont have", "do not have", "no phone", "no number", "no email",
        "none of", "rather not", "prefer not", "leave blank", "leave it blank", "no thanks",
        // Spanish / Portuguese / French phrases
        "no tengo", "no aplica", "não tenho", "nao tenho", "não se aplica", "nao se aplica",
        "je n'en ai pas", "pas de", "sans objet", "non applicable",
        // Chinese
        "没有", "沒有", "无", "跳过", "不适用",
        // Japanese
        "なし", "ありません", "スキップ", "該当なし", "無し",
        // Arabic
        "لا يوجد", "لا أملك", "تخطي", "لا ينطبق", "غير متوفر", "لا شيء",
        // Russian phrases
        "не имею", "нет телефона", "нет номера"
    )

    /**
     * True when the patient is declining/skipping a field rather than giving a value
     * ("none", "ninguno", "没有", "なし", "لا يوجد", …). Used by the caller for free-text
     * fields so such answers pass instead of being rejected as a malformed phone/email.
     */
    fun isDecline(raw: String): Boolean {
        val t = raw.trim().lowercase().trimEnd('.', '!', '。', '،')
        if (t.isEmpty()) return false
        if (t in DECLINE_EXACT) return true
        return DECLINE_CONTAINS.any { t.contains(it) }
    }

    fun kindOf(field: FormField): Kind {
        val id = field.id.lowercase()
        return when {
            field.fieldType == FieldType.DATE ||
                id.contains("date_of_birth") || id.endsWith("_dob") || id == "dob" -> Kind.DATE
            id.contains("email") -> Kind.EMAIL
            id.contains("phone") -> Kind.PHONE
            id.contains("ssn") || id.contains("social_security") -> Kind.SSN
            id == "zip" || id.endsWith("_zip") || id.contains("zipcode") -> Kind.ZIP
            id == "state" || id.endsWith("_state") -> Kind.STATE
            id.contains("name") && !id.contains("insurance") &&
                !id.contains("employer") && !id.contains("provider") -> Kind.NAME
            field.fieldType == FieldType.NUMBER -> Kind.NUMBER
            else -> Kind.TEXT
        }
    }

    fun validate(field: FormField, raw: String): Result {
        val value = raw.trim()
        // Only free-text-style fields get format checks.
        if (field.fieldType != FieldType.TEXT &&
            field.fieldType != FieldType.NUMBER &&
            field.fieldType != FieldType.DATE
        ) return Result(true, value)
        if (value.isBlank()) return Result(true, value)

        return when (kindOf(field)) {
            Kind.NAME -> {
                // A name needs at least one letter and no digits.
                val hasLetter = value.any { it.isLetter() }
                val hasDigit = value.any { it.isDigit() }
                if (hasLetter && !hasDigit) Result(true, value)
                else Result(false, value, R.string.validate_name)
            }
            Kind.PHONE -> {
                var digits = value.filter { it.isDigit() }
                if (digits.length == 11 && digits.startsWith("1")) digits = digits.substring(1)
                if (digits.length == 10) {
                    Result(true, "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}")
                } else Result(false, value, R.string.validate_phone)
            }
            Kind.EMAIL -> {
                val ok = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(value)
                if (ok) Result(true, value) else Result(false, value, R.string.validate_email)
            }
            Kind.ZIP -> {
                val digits = value.filter { it.isDigit() }
                when (digits.length) {
                    5 -> Result(true, digits)
                    9 -> Result(true, "${digits.substring(0, 5)}-${digits.substring(5)}")
                    else -> Result(false, value, R.string.validate_zip)
                }
            }
            Kind.SSN -> {
                val digits = value.filter { it.isDigit() }
                if (digits.length == 9)
                    Result(true, "${digits.substring(0, 3)}-${digits.substring(3, 5)}-${digits.substring(5)}")
                else Result(false, value, R.string.validate_ssn)
            }
            Kind.STATE -> {
                val up = value.uppercase()
                when {
                    up.length == 2 && up in US_STATES -> Result(true, up)
                    value.lowercase() in STATE_NAME_TO_CODE -> Result(true, STATE_NAME_TO_CODE.getValue(value.lowercase()))
                    else -> Result(false, value, R.string.validate_state)
                }
            }
            Kind.DATE -> normalizeDate(value)?.let { Result(true, it) }
                ?: Result(false, value, R.string.validate_date)
            Kind.NUMBER -> if (value.any { it.isDigit() }) Result(true, value)
                else Result(false, value, R.string.validate_number)
            Kind.TEXT -> Result(true, value)
        }
    }

    /**
     * Returns a normalized MM/DD/YYYY string for any reasonable date the patient
     * (or the conversation engine) might produce — numeric (MM/DD/YYYY, with -, /,
     * or . separators) OR spelled-out ("March 14 1985", "14 Mar 1985"). Lenient on
     * purpose: this is a sanity check, not a strict parser, so valid dates are never
     * bounced back with a format hint.
     */
    private fun normalizeDate(value: String): String? {
        val out = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
        // First try the common numeric forms via a fast split.
        val parts = value.split("/", "-", ".").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size == 3 && parts.all { it.all(Char::isDigit) }) {
            val a = parts[0].toIntOrNull(); val b = parts[1].toIntOrNull()
            var y = parts[2].toIntOrNull()
            if (a != null && b != null && y != null) {
                if (y < 100) y += if (y > 25) 1900 else 2000
                if (y in 1900..2100 && a in 1..12 && b in 1..31)
                    return "%02d/%02d/%04d".format(a, b, y)
            }
        }
        // Then try worded / alternative formats (mirrors the conversation engine).
        val cleaned = value.replace(Regex("""(\d+)(st|nd|rd|th)"""), "$1")
            .replace(",", " ").replace(Regex(" {2,}"), " ").trim()
        val patterns = listOf(
            "MM/dd/yyyy", "M/d/yyyy", "MM-dd-yyyy", "M-d-yyyy", "yyyy-MM-dd",
            "MMMM d yyyy", "MMM d yyyy", "d MMMM yyyy", "d MMM yyyy"
        )
        for (fmt in patterns) {
            try {
                val df = java.text.SimpleDateFormat(fmt, java.util.Locale.US).apply { isLenient = false }
                val date = df.parse(cleaned) ?: continue
                val y = java.util.Calendar.getInstance().apply { time = date }.get(java.util.Calendar.YEAR)
                if (y in 1900..2100) return out.format(date)
            } catch (_: Exception) { }
        }
        return null
    }
}
