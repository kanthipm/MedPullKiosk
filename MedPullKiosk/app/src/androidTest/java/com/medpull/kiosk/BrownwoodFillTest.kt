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
 * Trial run of the real AccelHealth (Brownwood) AcroForm fill with example data.
 * Writes the result to the app's external files dir for pull/review:
 *   /sdcard/Android/data/com.medpull.kiosk.debug/files/pdf_trial_bw/filled_*.pdf
 */
@RunWith(AndroidJUnit4::class)
class BrownwoodFillTest {

    private val TAG = "BrownwoodFillTest"

    private fun f(id: String, value: String, type: FieldType = FieldType.TEXT) =
        FormField(id = id, formId = "brownwood_intake", fieldName = id.replace('_', ' '),
            fieldType = type, value = value)

    @Test
    fun fillBrownwoodWithExampleData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filler = PdfFormFiller(context, PdfUtils(context))

        // signature bitmap
        val sigBmp = Bitmap.createBitmap(440, 90, Bitmap.Config.ARGB_8888)
        Canvas(sigBmp).apply {
            drawColor(Color.TRANSPARENT)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 40, 120); textSize = 52f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            drawText("Maria Gonzalez", 10f, 60f, p)
        }
        val sigFile = File(context.cacheDir, "bw_signature.png")
        sigFile.outputStream().use { sigBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val fields = listOf(
            f("preferred_language", "Spanish", FieldType.RADIO),
            f("patient_first_name", "Maria"),
            f("patient_last_name", "Gonzalez"),
            f("patient_middle_name", "Elena"),
            f("date_of_birth", "03/14/1985", FieldType.DATE),
            f("social_security_number", "123-45-6789"),
            f("mailing_address", "1420 Seaside Blvd"),
            f("apt_no", "3B"),
            f("city", "Brownwood"),
            f("state", "TX"),
            f("zip", "76801"),
            f("county", "Brown"),
            f("cell_phone", "(361) 555-0142"),
            f("home_phone", "(361) 555-0199"),
            f("work_phone", "(361) 555-0177"),
            f("emergency_contact", "Carlos Gonzalez (361) 555-0188"),
            f("ag_work_history", "Yes", FieldType.RADIO),
            f("ag_lived_away", "No", FieldType.RADIO),
            f("ag_stopped_disability", "No", FieldType.RADIO),
            f("birth_sex", "Female", FieldType.RADIO),
            f("current_gender", "Female", FieldType.RADIO),
            f("gender_identity", "Female", FieldType.RADIO),
            f("sexual_orientation", "Straight or Heterosexual", FieldType.RADIO),
            f("preferred_pronoun", "She, her, hers", FieldType.RADIO),
            f("race", "White, Asian", FieldType.MULTI_SELECT),
            f("ethnicity", "Hispanic or Latino", FieldType.RADIO),
            f("marital_status", "Married", FieldType.RADIO),
            f("us_veteran", "No", FieldType.RADIO),
            f("homeless_status", "Not Homeless", FieldType.RADIO),
            f("how_heard", "Friend/Family, Internet", FieldType.MULTI_SELECT),
            f("primary_insurance_name", "Blue Cross Blue Shield"),
            f("secondary_insurance_name", "Aetna"),
            f("consent_phone_messages", "Yes", FieldType.RADIO),
            f("consent_mail", "No", FieldType.RADIO),
            f("consent_text", "Yes", FieldType.RADIO),
            f("consent_email", "Yes", FieldType.RADIO),
            f("is_minor", "No", FieldType.RADIO),
            f("patient_signature", "signature:${sigFile.absolutePath}", FieldType.SIGNATURE)
        )

        val outputDir = File(context.getExternalFilesDir(null), "pdf_trial_bw")
        outputDir.listFiles()?.forEach { it.delete() }

        val result = filler.fillBrownwoodForm(fields, outputDir)
        assertNotNull("fillBrownwoodForm returned null", result)
        assertTrue("output PDF missing", result!!.exists() && result.length() > 0)
        Log.i(TAG, "BW_TRIAL_PDF=${result.absolutePath} size=${result.length()}")
        println("BW_TRIAL_PDF=${result.absolutePath} size=${result.length()}")
    }

    @Test
    fun fillBrownwoodMinorWithGuardians() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filler = PdfFormFiller(context, PdfUtils(context))

        val fields = listOf(
            f("preferred_language", "English", FieldType.RADIO),
            f("patient_first_name", "Diego"),
            f("patient_last_name", "Gonzalez"),
            f("date_of_birth", "08/02/2014", FieldType.DATE),
            f("mailing_address", "1420 Seaside Blvd"),
            f("city", "Brownwood"),
            f("state", "TX"),
            f("zip", "76801"),
            f("emergency_contact", "Maria Gonzalez (361) 555-0142"),
            f("birth_sex", "Male", FieldType.RADIO),
            f("race", "White", FieldType.MULTI_SELECT),
            f("ethnicity", "Hispanic or Latino", FieldType.RADIO),
            f("is_minor", "Yes", FieldType.RADIO),
            // Guardian 1 (lives with patient)
            f("guardian1_name", "Maria Gonzalez"),
            f("guardian1_relationship", "Mother", FieldType.RADIO),
            f("guardian1_same_address", "Yes", FieldType.RADIO),
            f("guardian1_dob", "03/14/1985", FieldType.DATE),
            f("guardian1_cell_phone", "(361) 555-0142"),
            f("guardian1_home_phone", "(361) 555-0199"),
            f("guardian1_work_phone", "(361) 555-0177"),
            f("guardian1_employer", "Brownwood ISD"),
            f("guardian1_ssn", "987-65-4321"),
            // Guardian 2 (different address)
            f("has_second_guardian", "Yes", FieldType.RADIO),
            f("guardian2_name", "Carlos Gonzalez"),
            f("guardian2_relationship", "Father", FieldType.RADIO),
            f("guardian2_same_address", "No", FieldType.RADIO),
            f("guardian2_city_state_zip", "Early, TX 76802"),
            f("guardian2_dob", "11/20/1983", FieldType.DATE),
            f("guardian2_cell_phone", "(361) 555-0188"),
            f("guardian2_employer", "Acme Farms"),
            f("guardian2_ssn", "111-22-3333")
        )

        val outputDir = File(context.getExternalFilesDir(null), "pdf_trial_bw_minor")
        outputDir.listFiles()?.forEach { it.delete() }
        val result = filler.fillBrownwoodForm(fields, outputDir)
        assertNotNull("minor fill returned null", result)
        assertTrue("minor PDF missing", result!!.exists() && result.length() > 0)
        Log.i(TAG, "BW_MINOR_PDF=${result.absolutePath} size=${result.length()}")
        println("BW_MINOR_PDF=${result.absolutePath} size=${result.length()}")
    }
}
