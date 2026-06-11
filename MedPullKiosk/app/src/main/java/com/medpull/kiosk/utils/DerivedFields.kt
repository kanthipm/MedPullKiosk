package com.medpull.kiosk.utils

import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FieldUpdate
import com.medpull.kiosk.data.models.FormField
import java.util.Calendar

/**
 * Deterministic field derivation: never ask the patient for something already
 * derivable from what they've given.
 *
 * Today that means age facts from a date of birth: a numeric "age" field and
 * any "are you under/over 18" style Yes/No radio are NOT asked during intake —
 * they are computed the moment the sibling DOB field is answered. Pure math,
 * no AI, no confirmation step needed.
 *
 * Pairing is by id prefix so multi-person forms stay correct:
 * "parent1_age" derives from "parent1_date_of_birth", never from the patient's
 * "date_of_birth".
 */
object DerivedFields {

    private enum class AgeSemantics { AGE_NUMBER, IS_UNDER_18, IS_OVER_18 }

    /** True for any date-of-birth field ("date_of_birth", "renewal_date_of_birth", "policyholder_dob", …). */
    fun isDobField(field: FormField): Boolean {
        val id = field.id.lowercase()
        return id == "dob" || id.endsWith("_dob") || id.endsWith("date_of_birth")
    }

    /** "parent1_date_of_birth" → "parent1_", "date_of_birth" → "", "policyholder_dob" → "policyholder_". */
    private fun dobPrefix(dobId: String): String {
        val id = dobId.lowercase()
        return when {
            id == "dob" -> ""
            id.endsWith("_dob") -> id.removeSuffix("dob")
            id.endsWith("date_of_birth") -> id.removeSuffix("date_of_birth")
            else -> ""
        }
    }

    private fun isYesNo(field: FormField): Boolean {
        val opts = field.options.map { it.trim().lowercase() }
        return opts.size == 2 && opts.containsAll(listOf("yes", "no"))
    }

    private fun semanticsOf(field: FormField): AgeSemantics? {
        val id = field.id.lowercase()
        val text = "${field.question ?: ""} ${field.fieldName}".lowercase()
        return when {
            field.fieldType == FieldType.RADIO && isYesNo(field) -> when {
                id.contains("minor") || id.contains("under_18") || id.contains("under18") ||
                    text.contains("under 18") || text.contains("minor") -> AgeSemantics.IS_UNDER_18
                id.contains("over_18") || id.contains("over18") || id.contains("18_or_older") ||
                    text.contains("over 18") || text.contains("over age 18") ||
                    text.contains("18 or older") || text.contains("18 years or older") -> AgeSemantics.IS_OVER_18
                else -> null
            }
            (field.fieldType == FieldType.NUMBER || field.fieldType == FieldType.TEXT) &&
                (id == "age" || id.endsWith("_age")) -> AgeSemantics.AGE_NUMBER
            else -> null
        }
    }

    /** The DOB field whose prefix is the longest prefix of [field]'s id (e.g. "" or "parent1_"). */
    private fun dobSourceFor(field: FormField, allFields: List<FormField>): FormField? =
        allFields
            .filter { it.id != field.id && isDobField(it) }
            .filter { field.id.lowercase().startsWith(dobPrefix(it.id)) }
            .maxByOrNull { dobPrefix(it.id).length }

    /**
     * True when [field] should NOT be asked yet: it's an age-derivable field and
     * its sibling DOB field is still pending (blank, not skipped). Once the DOB
     * arrives, [updatesFor] fills this field; if the DOB ends up skipped or
     * unparseable, the field becomes askable again.
     */
    fun isDeferrableAgeField(
        field: FormField,
        allFields: List<FormField>,
        skippedIds: Set<String>
    ): Boolean {
        if (semanticsOf(field) == null) return false
        val source = dobSourceFor(field, allFields) ?: return false
        return source.id !in skippedIds && source.value.isNullOrBlank()
    }

    /**
     * Derived updates triggered by answering [dobField] with [dobValue].
     * Overwrites prior values on a DOB re-edit — age facts are a pure function
     * of the DOB, so the derivation always wins.
     */
    fun updatesFor(
        dobField: FormField,
        dobValue: String,
        allFields: List<FormField>
    ): List<FieldUpdate> {
        if (!isDobField(dobField)) return emptyList()
        val age = computeAge(dobValue) ?: return emptyList()
        val prefix = dobPrefix(dobField.id)
        return allFields.mapNotNull { f ->
            val sem = semanticsOf(f) ?: return@mapNotNull null
            val source = dobSourceFor(f, allFields) ?: return@mapNotNull null
            if (dobPrefix(source.id) != prefix) return@mapNotNull null
            val value = when (sem) {
                AgeSemantics.AGE_NUMBER -> age.toString()
                AgeSemantics.IS_UNDER_18 -> yesNoOption(f, age < 18)
                AgeSemantics.IS_OVER_18 -> yesNoOption(f, age >= 18)
            } ?: return@mapNotNull null
            if (f.value == value) null else FieldUpdate(f.id, value, 1.0f)
        }
    }

    /** Resolve to the field's own option casing so option matching stays exact. */
    private fun yesNoOption(field: FormField, yes: Boolean): String? {
        val target = if (yes) "yes" else "no"
        return field.options.firstOrNull { it.trim().lowercase() == target }
    }

    /** Whole years for an MM/DD/YYYY (or M/D/YYYY, dashes ok) date. Mirrors PdfFormFiller. */
    fun computeAge(dob: String, today: Calendar = Calendar.getInstance()): Int? {
        val parts = dob.split("/", "-").map { it.trim() }
        if (parts.size != 3) return null
        val mm = parts[0].toIntOrNull() ?: return null
        val dd = parts[1].toIntOrNull() ?: return null
        var yyyy = parts[2].toIntOrNull() ?: return null
        if (yyyy < 100) yyyy += if (yyyy > 25) 1900 else 2000
        if (mm !in 1..12 || dd !in 1..31) return null
        var age = today.get(Calendar.YEAR) - yyyy
        val m = today.get(Calendar.MONTH) + 1
        val d = today.get(Calendar.DAY_OF_MONTH)
        if (m < mm || (m == mm && d < dd)) age--
        return if (age in 0..130) age else null
    }
}
