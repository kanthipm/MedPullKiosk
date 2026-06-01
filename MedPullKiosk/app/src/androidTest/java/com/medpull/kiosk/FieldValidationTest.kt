package com.medpull.kiosk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.utils.FieldValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FieldValidationTest {

    private fun field(id: String, type: FieldType) =
        FormField(id = id, formId = "t", fieldName = id, fieldType = type)

    @Test fun phone_validatesAndFormats() {
        val f = field("cell_phone", FieldType.NUMBER)
        assertEquals("(361) 555-0142", FieldValidation.validate(f, "3615550142").normalized)
        assertEquals("(361) 555-0142", FieldValidation.validate(f, "1-361-555-0142").normalized)
        assertTrue(FieldValidation.validate(f, "(361) 555-0142").ok)
        assertFalse(FieldValidation.validate(f, "555-012").ok)
    }

    @Test fun email_validates() {
        val f = field("email", FieldType.NUMBER)
        assertTrue(FieldValidation.validate(f, "maria@example.com").ok)
        assertFalse(FieldValidation.validate(f, "maria.example.com").ok)
        assertFalse(FieldValidation.validate(f, "maria@example").ok)
    }

    @Test fun zip_validates() {
        val f = field("zip", FieldType.NUMBER)
        assertEquals("76801", FieldValidation.validate(f, "76801").normalized)
        assertEquals("76801-1234", FieldValidation.validate(f, "768011234").normalized)
        assertFalse(FieldValidation.validate(f, "768").ok)
    }

    @Test fun ssn_validatesAndFormats() {
        val f = field("social_security_number", FieldType.TEXT)
        assertEquals("123-45-6789", FieldValidation.validate(f, "123456789").normalized)
        assertFalse(FieldValidation.validate(f, "123").ok)
    }

    @Test fun date_validatesAndNormalizes() {
        val f = field("date_of_birth", FieldType.DATE)
        assertEquals("03/14/1985", FieldValidation.validate(f, "3/14/1985").normalized)
        assertEquals("03/14/1985", FieldValidation.validate(f, "3-14-1985").normalized)
        // Worded dates (what voice/offline parsing often yields) must be accepted.
        assertEquals("03/14/1985", FieldValidation.validate(f, "March 14, 1985").normalized)
        assertEquals("03/14/1985", FieldValidation.validate(f, "14 Mar 1985").normalized)
        assertFalse(FieldValidation.validate(f, "sometime in spring").ok)
        assertFalse(FieldValidation.validate(f, "13/40/1985").ok)
    }

    @Test fun state_validatesAndNormalizes() {
        val f = field("state", FieldType.TEXT)
        assertEquals("TX", FieldValidation.validate(f, "texas").normalized)
        assertTrue(FieldValidation.validate(f, "TX").ok)
        assertFalse(FieldValidation.validate(f, "ZZ").ok)
    }

    @Test fun name_rejectsDigits() {
        val f = field("patient_first_name", FieldType.TEXT)
        assertTrue(FieldValidation.validate(f, "Maria").ok)
        assertFalse(FieldValidation.validate(f, "Maria3").ok)
    }

    @Test fun optionFieldsAndInsuranceArePassthrough() {
        // Radio options are never format-checked.
        assertTrue(FieldValidation.validate(field("marital_status", FieldType.RADIO), "Married").ok)
        // "insurance name" must not be treated as a person name (digits allowed).
        val ins = field("primary_insurance_name", FieldType.TEXT)
        assertTrue(FieldValidation.validate(ins, "Aetna PPO 123").ok)
    }
}
