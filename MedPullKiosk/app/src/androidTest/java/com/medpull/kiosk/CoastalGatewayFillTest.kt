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
 * Trial run of the real Coastal Gateway PDF fill with example patient data.
 *
 * This exercises the production [PdfFormFiller.fillCoastalGatewayForm] path
 * (real PdfBox, real form asset, real coordinate map) and writes the result to
 * the app's external files dir so it can be pulled off the device for review:
 *
 *   /sdcard/Android/data/com.medpull.kiosk.debug/files/pdf_trial/filled_*.pdf
 */
@RunWith(AndroidJUnit4::class)
class CoastalGatewayFillTest {

    private val TAG = "CoastalGatewayFillTest"

    private fun f(
        id: String,
        value: String,
        type: FieldType = FieldType.TEXT
    ) = FormField(
        id = id,
        formId = "coastal_gateway_intake",
        fieldName = id.replace('_', ' '),
        fieldType = type,
        value = value
    )

    @Test
    fun fillCoastalGatewayWithExampleData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filler = PdfFormFiller(context, PdfUtils(context))

        // ── Build a realistic signature bitmap (used by final_signature) ────────
        val sigBmp = Bitmap.createBitmap(420, 90, Bitmap.Config.ARGB_8888)
        Canvas(sigBmp).apply {
            drawColor(Color.TRANSPARENT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 40, 120)
                textSize = 54f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            drawText("Maria Gonzalez", 12f, 60f, paint)
        }
        val sigFile = File(context.cacheDir, "trial_signature.png")
        sigFile.outputStream().use { sigBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // ── Example patient answers (cover every placement id) ──────────────────
        val fields = listOf(
            f("patient_full_name", "Maria Gonzalez"),
            f("date_of_birth", "03/14/1985", FieldType.DATE),
            f("mailing_address_street", "1420 Seaside Blvd, Apt 3B"),
            f("mailing_city", "Corpus Christi"),
            f("mailing_state", "TX"),
            f("mailing_zip", "78401"),
            f("physical_same_as_mailing", "Yes", FieldType.RADIO),
            f("physical_address_street", "1420 Seaside Blvd, Apt 3B"),
            f("physical_city", "Corpus Christi"),
            f("physical_state", "TX"),
            f("physical_zip", "78401"),
            f("cell_phone", "(361) 555-0142"),
            f("home_phone", "(361) 555-0199"),
            f("email", "maria.gonzalez@example.com"),
            f("preferred_language", "English", FieldType.RADIO),
            f("sex_assigned_at_birth", "Female", FieldType.RADIO),
            f("marital_status", "Married", FieldType.RADIO),
            f("gender_identity", "Female", FieldType.RADIO),
            f("race", "White, Asian", FieldType.MULTI_SELECT),
            f("ethnicity", "Hispanic or Latino", FieldType.RADIO),
            f("emergency_contact_name", "Carlos Gonzalez"),
            f("emergency_contact_phone", "(361) 555-0177"),
            f("primary_insurance_provider", "Blue Cross Blue Shield"),
            f("primary_insurance_id", "XJK123456789"),
            f("primary_insurance_group", "GRP-4456"),
            f("policyholder_name", "Maria Gonzalez"),
            f("policyholder_dob", "03/14/1985", FieldType.DATE),
            f("policyholder_relationship", "Self"),
            f("secondary_insurance_provider", "Aetna"),
            f("secondary_insurance_id", "AET998877"),
            f("secondary_insurance_group", "GRP-2231"),
            f("final_signature", "signature:${sigFile.absolutePath}", FieldType.SIGNATURE)
        )

        val outputDir = File(context.getExternalFilesDir(null), "pdf_trial")
        // Clean prior runs so only this trial's PDF is present.
        outputDir.listFiles()?.forEach { it.delete() }

        val result = filler.fillCoastalGatewayForm(fields, outputDir)

        assertNotNull("fillCoastalGatewayForm returned null", result)
        assertTrue("output PDF does not exist", result!!.exists())
        assertTrue("output PDF is empty", result.length() > 0)

        Log.i(TAG, "TRIAL_PDF_PATH=${result.absolutePath} size=${result.length()}")
        println("TRIAL_PDF_PATH=${result.absolutePath} size=${result.length()}")
    }
}
