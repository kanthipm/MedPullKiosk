package com.medpull.kiosk.utils

import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DerivedFieldsTest {

    private fun field(
        id: String,
        type: FieldType = FieldType.TEXT,
        value: String? = null,
        options: List<String> = emptyList(),
        question: String? = null
    ) = FormField(
        id = id, formId = "f", fieldName = id, fieldType = type,
        value = value, options = options, question = question
    )

    private val dob = field("date_of_birth", FieldType.DATE)
    private val age = field("age", FieldType.NUMBER)
    private val isMinor = field(
        "is_minor", FieldType.RADIO,
        options = listOf("Yes", "No"),
        question = "Are you under 18 years old?"
    )

    private fun fixedToday(y: Int, m: Int, d: Int): Calendar =
        Calendar.getInstance().apply { set(y, m - 1, d) }

    // ─── computeAge ──────────────────────────────────────────────────────────

    @Test
    fun `age counts a birthday that has passed this year`() {
        assertEquals(41, DerivedFields.computeAge("03/14/1985", fixedToday(2026, 6, 10)))
    }

    @Test
    fun `age counts a birthday still ahead this year`() {
        assertEquals(40, DerivedFields.computeAge("09/14/1985", fixedToday(2026, 6, 10)))
    }

    @Test
    fun `garbage dates produce no age`() {
        assertNull(DerivedFields.computeAge("not a date"))
        assertNull(DerivedFields.computeAge("13/45/1985", fixedToday(2026, 6, 10)))
    }

    // ─── updatesFor ──────────────────────────────────────────────────────────

    @Test
    fun `adult DOB fills age and answers minor question No`() {
        val updates = DerivedFields.updatesFor(dob, "03/14/1985", listOf(dob, age, isMinor))
        assertEquals(2, updates.size)
        val ageVal = updates.first { it.fieldId == "age" }.value.toInt()
        assertTrue(ageVal >= 18)
        assertEquals("No", updates.first { it.fieldId == "is_minor" }.value)
    }

    @Test
    fun `recent DOB answers minor question Yes`() {
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val updates = DerivedFields.updatesFor(dob, "01/01/${thisYear - 5}", listOf(dob, age, isMinor))
        assertEquals("Yes", updates.first { it.fieldId == "is_minor" }.value)
    }

    @Test
    fun `parent DOB does not touch the patient's age fields`() {
        val parentDob = field("parent1_date_of_birth", FieldType.DATE)
        val updates = DerivedFields.updatesFor(parentDob, "03/14/1985", listOf(dob, parentDob, age, isMinor))
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `non DOB fields derive nothing`() {
        assertTrue(DerivedFields.updatesFor(field("first_name"), "Maria", listOf(age)).isEmpty())
    }

    @Test
    fun `over 18 style question answers Yes for adults`() {
        val over18 = field(
            "patient_over_18", FieldType.RADIO,
            options = listOf("Yes", "No"),
            question = "Are you over 18?"
        )
        val updates = DerivedFields.updatesFor(dob, "03/14/1985", listOf(dob, over18))
        assertEquals("Yes", updates.first { it.fieldId == "patient_over_18" }.value)
    }

    // ─── deferral ────────────────────────────────────────────────────────────

    @Test
    fun `age fields defer while DOB is pending`() {
        val all = listOf(age, dob, isMinor)
        assertTrue(DerivedFields.isDeferrableAgeField(age, all, emptySet()))
        assertTrue(DerivedFields.isDeferrableAgeField(isMinor, all, emptySet()))
    }

    @Test
    fun `age fields stop deferring once DOB is answered or skipped`() {
        val answeredDob = dob.copy(value = "03/14/1985")
        assertFalse(DerivedFields.isDeferrableAgeField(age, listOf(age, answeredDob), emptySet()))
        assertFalse(DerivedFields.isDeferrableAgeField(age, listOf(age, dob), setOf("date_of_birth")))
    }

    @Test
    fun `fields without a DOB sibling never defer`() {
        assertFalse(DerivedFields.isDeferrableAgeField(age, listOf(age), emptySet()))
        assertFalse(DerivedFields.isDeferrableAgeField(field("first_name"), listOf(field("first_name"), dob), emptySet()))
    }
}
