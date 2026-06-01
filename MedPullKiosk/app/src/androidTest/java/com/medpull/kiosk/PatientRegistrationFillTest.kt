package com.medpull.kiosk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.utils.PdfFormFiller
import com.medpull.kiosk.utils.PdfUtils
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Trial fill of the AccelHealth / Brownswood "patient_registration" AcroForm with
 * example data, using Kanthi's schema field ids. Writes to the app's external files
 * dir for pull/review:
 *   /sdcard/Android/data/com.medpull.kiosk.debug/files/pdf_trial_pr[_minor]/filled_*.pdf
 */
@RunWith(AndroidJUnit4::class)
class PatientRegistrationFillTest {

    private val TAG = "PatientRegFillTest"

    private fun f(id: String, value: String, type: FieldType = FieldType.TEXT) =
        FormField(id = id, formId = "patient_registration", fieldName = id.replace('_', ' '),
            fieldType = type, value = value)

    private fun signature(context: android.content.Context): String {
        val bmp = Bitmap.createBitmap(440, 90, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.TRANSPARENT)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 40, 120); textSize = 52f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            drawText("Maria Gonzalez", 10f, 60f, p)
        }
        val file = File(context.cacheDir, "pr_signature.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "signature:${file.absolutePath}"
    }

    @Test
    fun fillAdult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filler = PdfFormFiller(context, PdfUtils(context))
        val fields = listOf(
            f("preferred_language", "Spanish", FieldType.RADIO),
            f("first_name", "Maria"),
            f("last_name", "Gonzalez"),
            f("middle_name", "Elena"),
            f("social_security_number", "123-45-6789"),
            f("date_of_birth", "03/14/1985", FieldType.DATE),   // age left blank → computed
            f("is_minor", "No", FieldType.RADIO),
            f("mailing_address", "1420 Seaside Blvd"),
            f("apt_no", "3B"),
            f("city", "Brownwood"),
            f("state", "TX"),
            f("zip", "76801"),
            f("county", "Brown"),
            f("home_phone", "(361) 555-0199"),
            f("work_phone", "(361) 555-0177"),
            f("cell_phone", "(361) 555-0142"),
            f("emergency_contact_name", "Carlos Gonzalez"),
            f("emergency_contact_phone", "(361) 555-0188"),
            f("birth_sex", "Female", FieldType.RADIO),
            f("current_gender", "Female", FieldType.RADIO),
            f("gender_identity", "Female", FieldType.RADIO),
            f("sexual_orientation", "Straight or Heterosexual", FieldType.RADIO),
            f("race", "White, Asian", FieldType.MULTI_SELECT),
            f("preferred_pronoun", "She, Her, Hers", FieldType.RADIO),
            f("ethnicity", "Hispanic or Latino", FieldType.RADIO),
            f("marital_status", "Married", FieldType.RADIO),
            f("us_veteran", "No", FieldType.RADIO),
            f("agriculture_work_2yr", "Yes", FieldType.RADIO),
            f("agriculture_away_from_home", "No", FieldType.RADIO),
            f("agriculture_disability_stopped", "No", FieldType.RADIO),
            f("homeless_status", "Not Homeless", FieldType.RADIO),
            f("primary_insurance_name", "Blue Cross Blue Shield"),
            f("secondary_insurance_name", "Aetna"),
            f("leave_phone_messages", "Yes", FieldType.RADIO),
            f("mail_correspondence", "No", FieldType.RADIO),
            f("text_reminders", "Yes", FieldType.RADIO),
            f("email_information", "Yes", FieldType.RADIO),
            f("how_heard_about", "Friend/Family", FieldType.RADIO),
            f("patient_signature", signature(context), FieldType.SIGNATURE)
        )
        val outDir = File(context.getExternalFilesDir(null), "pdf_trial_pr")
        outDir.listFiles()?.forEach { it.delete() }
        val result = filler.fillPatientRegistrationForm(fields, outDir)
        assertNotNull("fill returned null", result)
        assertTrue("PDF missing", result!!.exists() && result.length() > 0)
        Log.i(TAG, "PR_ADULT_PDF=${result.absolutePath}")
        println("PR_ADULT_PDF=${result.absolutePath}")
    }

    @Test
    fun fillMinorWithGuardians() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filler = PdfFormFiller(context, PdfUtils(context))
        val fields = listOf(
            f("preferred_language", "English", FieldType.RADIO),
            f("first_name", "Diego"),
            f("last_name", "Gonzalez"),
            f("date_of_birth", "08/02/2014", FieldType.DATE),
            f("is_minor", "Yes", FieldType.RADIO),
            f("mailing_address", "1420 Seaside Blvd"),
            f("city", "Brownwood"),
            f("state", "TX"),
            f("zip", "76801"),
            f("emergency_contact_name", "Maria Gonzalez"),
            f("emergency_contact_phone", "(361) 555-0142"),
            f("birth_sex", "Male", FieldType.RADIO),
            f("race", "White", FieldType.MULTI_SELECT),
            f("ethnicity", "Hispanic or Latino", FieldType.RADIO),
            // Guardian 1 (lives with patient)
            f("parent1_name", "Maria Gonzalez"),
            f("parent1_relationship", "Mother", FieldType.RADIO),
            f("parent1_same_as_above", "Yes", FieldType.RADIO),
            f("parent1_date_of_birth", "03/14/1985", FieldType.DATE),
            f("parent1_cell_phone", "(361) 555-0142"),
            f("parent1_home_phone", "(361) 555-0199"),
            f("parent1_employer", "Brownwood ISD"),
            f("parent1_ssn", "987-65-4321"),
            // Guardian 2 (different address)
            f("parent2_name", "Carlos Gonzalez"),
            f("parent2_relationship", "Father", FieldType.RADIO),
            f("parent2_same_as_above", "No", FieldType.RADIO),
            f("parent2_city_state_zip", "Early, TX 76802"),
            f("parent2_date_of_birth", "11/20/1983", FieldType.DATE),
            f("parent2_cell_phone", "(361) 555-0188"),
            f("parent2_employer", "Acme Farms"),
            f("parent2_ssn", "111-22-3333")
        )
        val outDir = File(context.getExternalFilesDir(null), "pdf_trial_pr_minor")
        outDir.listFiles()?.forEach { it.delete() }
        val result = filler.fillPatientRegistrationForm(fields, outDir)
        assertNotNull("minor fill returned null", result)
        assertTrue("minor PDF missing", result!!.exists() && result.length() > 0)
        Log.i(TAG, "PR_MINOR_PDF=${result.absolutePath}")
        println("PR_MINOR_PDF=${result.absolutePath}")
    }
}
