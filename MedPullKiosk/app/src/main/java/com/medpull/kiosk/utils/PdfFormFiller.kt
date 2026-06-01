package com.medpull.kiosk.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

/**
 * Fills the Coastal Gateway intake form PDF with patient answers.
 *
 * Strategy (in priority order):
 *  1. AcroForm fields — if the PDF has interactive form fields, fill them by name.
 *     This is pixel-perfect positioning with no coordinate guessing.
 *  2. Text-position overlay — extract text positions with PDFTextStripper, match
 *     field labels, overlay answers in blue near each label.
 *  3. Formatted summary PDF — if neither above works (scanned / image PDF), generate
 *     a professional multi-section summary document that looks like a patient record,
 *     not a plain list.
 */
@Singleton
class PdfFormFiller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfUtils: PdfUtils
) {
    companion object {
        private const val TAG = "PdfFormFiller"
        private const val COASTAL_GATEWAY_PDF = "forms/Coastal_Gateway_Intake_Form.pdf"
        private const val ANSWER_FONT_SIZE = 9f
        private const val LABEL_MATCH_THRESHOLD = 0.55
    }

    init {
        PDFBoxResourceLoader.init(context)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun fillCoastalGatewayForm(fields: List<FormField>, outputDir: File): File? {
        // Coastal Gateway is a flat (non-AcroForm) printed form, so generic
        // label-matching can't place values into its blanks/checkboxes. We use an
        // explicit, hand-verified coordinate map instead. If that places nothing
        // (e.g. the asset changed), fall back to the clean formatted summary.
        return try {
            fillCoastalGatewayPrecise(fields, outputDir)
                ?: createFormattedSummaryPdf(coastalSummaryFields(fields), outputDir, "Coastal Gateway — Patient Intake")
        } catch (e: Exception) {
            Log.e(TAG, "Precise Coastal Gateway fill failed — using summary", e)
            createFormattedSummaryPdf(coastalSummaryFields(fields), outputDir, "Coastal Gateway — Patient Intake")
        }
    }

    private fun coastalSummaryFields(fields: List<FormField>): List<FormField> =
        fields.filter { !it.value.isNullOrBlank() && it.fieldType != FieldType.STATIC_LABEL && it.value != "delivered" }

    // ─── Coastal Gateway: explicit placement map ─────────────────────────────
    //
    // Coordinates are in the PDF's TOP-LEFT points (612 x 792), read directly
    // from the form's text positions. They are converted to PDFBox's bottom-left
    // origin at draw time via `pdfY = pageHeight - y`. `Opt` (x,y) is where an "X"
    // is stamped on a "___option" blank; text is drawn with its baseline at y.

    private data class Opt(val value: String, val x: Float, val y: Float)

    private sealed interface Place {
        val page: Int
        data class Text(override val page: Int, val x: Float, val y: Float) : Place
        data class Date3(override val page: Int, val x1: Float, val x2: Float, val x3: Float, val y: Float) : Place
        data class Choice(override val page: Int, val opts: List<Opt>) : Place
        data class Multi(override val page: Int, val opts: List<Opt>) : Place
        data class Sign(override val page: Int, val x: Float, val y: Float) : Place
    }

    private val coastalPlacements: Map<String, List<Place>> = mapOf(
        "patient_full_name" to listOf(Place.Text(0, 145f, 96f), Place.Text(1, 228f, 64f)),
        "date_of_birth" to listOf(Place.Date3(0, 493f, 521f, 548f, 96f), Place.Text(1, 410f, 64f)),
        "mailing_address_street" to listOf(Place.Text(0, 30f, 136f)),
        "mailing_city" to listOf(Place.Text(0, 24f, 180f)),
        "mailing_state" to listOf(Place.Text(0, 150f, 180f)),
        "mailing_zip" to listOf(Place.Text(0, 199f, 180f)),
        "physical_same_as_mailing" to listOf(Place.Choice(0, listOf(Opt("Yes", 106f, 214f)))),
        "physical_address_street" to listOf(Place.Text(0, 30f, 240f)),
        "physical_city" to listOf(Place.Text(0, 23f, 265f)),
        "physical_state" to listOf(Place.Text(0, 91f, 265f)),
        "physical_zip" to listOf(Place.Text(0, 140f, 265f)),
        "cell_phone" to listOf(Place.Text(0, 70f, 317f)),
        "home_phone" to listOf(Place.Text(0, 70f, 332f)),
        "email" to listOf(Place.Text(0, 90f, 367f)),
        "preferred_language" to listOf(Place.Choice(0, listOf(
            Opt("English", 22f, 424f), Opt("Español", 69f, 424f), Opt("Tiếng Việt", 124f, 424f)
        ))),
        "sex_assigned_at_birth" to listOf(Place.Choice(0, listOf(
            Opt("Female", 377f, 110f), Opt("Male", 441f, 110f)
        ))),
        "marital_status" to listOf(Place.Choice(0, listOf(
            Opt("Single", 359f, 140f), Opt("Married", 437f, 140f),
            Opt("Divorced", 359f, 152f), Opt("Widowed", 427f, 152f)
        ))),
        "gender_identity" to listOf(Place.Choice(0, listOf(
            Opt("Female", 351f, 184f), Opt("Male", 390f, 184f),
            Opt("Transgender Female", 351f, 197f), Opt("Transgender Male", 351f, 213f)
        ))),
        "race" to listOf(Place.Multi(0, listOf(
            Opt("White", 351f, 300f), Opt("African American / Black", 388f, 300f), Opt("Asian", 482f, 300f),
            Opt("American Indian or Alaska Native", 351f, 318f), Opt("Native Hawaiian", 495f, 318f),
            Opt("Other Pacific Islander", 351f, 331f), Opt("Decline to specify", 440f, 331f)
        ))),
        "ethnicity" to listOf(Place.Choice(0, listOf(
            Opt("Not Hispanic or Latino", 351f, 369f), Opt("Hispanic or Latino", 351f, 383f),
            Opt("Decline to specify", 429f, 369f)
        ))),
        "emergency_contact_name" to listOf(Place.Text(0, 395f, 476f)),
        "emergency_contact_phone" to listOf(Place.Text(0, 392f, 497f)),
        "representative_name" to listOf(Place.Text(0, 70f, 476f)),
        "representative_relationship" to listOf(Place.Text(0, 210f, 497f)),
        "primary_insurance_provider" to listOf(Place.Text(0, 130f, 546f)),
        "primary_insurance_id" to listOf(Place.Text(0, 45f, 573f)),
        "primary_insurance_group" to listOf(Place.Text(0, 390f, 573f)),
        "policyholder_name" to listOf(Place.Text(0, 80f, 601f)),
        "policyholder_dob" to listOf(Place.Date3(0, 100f, 128f, 152f, 626f)),
        "policyholder_relationship" to listOf(Place.Text(0, 390f, 626f)),
        "secondary_insurance_provider" to listOf(Place.Text(0, 130f, 655f)),
        "secondary_insurance_id" to listOf(Place.Text(0, 45f, 682f)),
        "secondary_insurance_group" to listOf(Place.Text(0, 390f, 682f)),
        "final_signature" to listOf(
            Place.Sign(2, 130f, 562f), Place.Sign(2, 130f, 617f),
            Place.Sign(6, 130f, 436f), Place.Sign(6, 130f, 477f)
        )
    )

    private fun normalizeOpt(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Fills the real Coastal Gateway PDF using [coastalPlacements]. Returns null
     * (so the caller can fall back to a summary) if nothing could be placed.
     */
    private fun fillCoastalGatewayPrecise(fields: List<FormField>, outputDir: File): File? {
        outputDir.mkdirs()
        val document = PDDocument.load(context.assets.open(COASTAL_GATEWAY_PDF))
        val streams = HashMap<Int, PDPageContentStream>()
        var placed = 0
        try {
            fun streamFor(pageIdx: Int): PDPageContentStream = streams.getOrPut(pageIdx) {
                PDPageContentStream(
                    document, document.getPage(pageIdx),
                    PDPageContentStream.AppendMode.APPEND, true, true
                )
            }
            val byId = fields.associateBy { it.id }
            for ((fieldId, places) in coastalPlacements) {
                val field = byId[fieldId] ?: continue
                val value = field.value
                if (value.isNullOrBlank() || value == "delivered") continue
                for (pl in places) {
                    // Coordinates in `coastalPlacements` are relative to the page's
                    // MediaBox top-left. Some pages of this form have a non-zero
                    // MediaBox origin (e.g. [-11.96, 11.99, 600.04, 803.99]), so we
                    // must anchor to the box's actual lower-left X and upper-right Y
                    // rather than assuming (0,0)/pageHeight — otherwise everything is
                    // shifted by the origin offset.
                    val box = document.getPage(pl.page).mediaBox
                    val ox = box.lowerLeftX          // content x = ox + placement x
                    val topY = box.upperRightY        // content baseline y = topY - placement y
                    try {
                        when (pl) {
                            is Place.Text -> {
                                drawText(streamFor(pl.page), ox + pl.x, topY - pl.y, value)
                                placed++
                            }
                            is Place.Date3 -> {
                                val parts = value.split("/", "-").map { it.trim() }
                                if (parts.size == 3) {
                                    drawText(streamFor(pl.page), ox + pl.x1, topY - pl.y, parts[0])
                                    drawText(streamFor(pl.page), ox + pl.x2, topY - pl.y, parts[1])
                                    drawText(streamFor(pl.page), ox + pl.x3, topY - pl.y, parts[2])
                                } else {
                                    drawText(streamFor(pl.page), ox + pl.x1, topY - pl.y, value)
                                }
                                placed++
                            }
                            is Place.Choice -> {
                                val key = normalizeOpt(value)
                                pl.opts.firstOrNull { normalizeOpt(it.value) == key }?.let { o ->
                                    drawX(streamFor(pl.page), ox + o.x, topY - o.y); placed++
                                }
                            }
                            is Place.Multi -> {
                                val selected = value.split(",").map { normalizeOpt(it) }.filter { it.isNotBlank() }
                                for (o in pl.opts) {
                                    if (normalizeOpt(o.value) in selected) {
                                        drawX(streamFor(pl.page), ox + o.x, topY - o.y); placed++
                                    }
                                }
                            }
                            is Place.Sign -> {
                                if (drawSignature(document, streamFor(pl.page), ox + pl.x, topY - pl.y, value)) placed++
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not place $fieldId on page ${pl.page}", e)
                    }
                }
            }
            streams.values.forEach { it.close() }
            streams.clear()
            if (placed == 0) return null
            val out = File(outputDir, "filled_${System.currentTimeMillis()}.pdf")
            document.save(out)
            Log.d(TAG, "Coastal Gateway precise fill: $placed marks placed")
            return out
        } finally {
            streams.values.forEach { runCatching { it.close() } }
            document.close()
        }
    }

    private fun drawText(stream: PDPageContentStream, x: Float, y: Float, raw: String) {
        val text = raw.replace('\n', ' ').replace('\r', ' ').trim().take(60)
        stream.setFont(PDType1Font.HELVETICA, ANSWER_FONT_SIZE)
        stream.setNonStrokingColor(0.07f, 0.25f, 0.70f)
        stream.beginText()
        stream.newLineAtOffset(x, y)
        try {
            stream.showText(text)
        } catch (e: Exception) {
            // Standard-14 Helvetica can't encode some glyphs (e.g. diacritics) —
            // fall back to an ASCII-only rendering so the field still shows.
            stream.showText(text.replace(Regex("[^\\x20-\\x7E]"), ""))
        }
        stream.endText()
    }

    private fun drawX(stream: PDPageContentStream, x: Float, y: Float) {
        stream.setFont(PDType1Font.HELVETICA_BOLD, 10f)
        stream.setNonStrokingColor(0.07f, 0.25f, 0.70f)
        stream.beginText()
        stream.newLineAtOffset(x, y)
        stream.showText("X")
        stream.endText()
    }

    private fun drawSignature(
        document: PDDocument, stream: PDPageContentStream, x: Float, yBaseline: Float, value: String
    ): Boolean {
        val path = value.removePrefix("signature:")
        val file = File(path).takeIf { it.exists() } ?: return false
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        val maxW = 150f
        val maxH = 26f
        val scale = minOf(maxW / bmp.width, maxH / bmp.height, 1f)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val image = LosslessFactory.createFromImage(document, bmp)
        stream.drawImage(image, x, yBaseline, w, h)
        return true
    }

    fun fillForm(
        fields: List<FormField>,
        pdfAssetPath: String,
        formName: String,
        outputDir: File
    ): File? {
        var document: PDDocument? = null
        return try {
            outputDir.mkdirs()
            val inputStream = try {
                context.assets.open(pdfAssetPath)
            } catch (e: java.io.FileNotFoundException) {
                Log.i(TAG, "No PDF template at $pdfAssetPath — generating formatted summary")
                return createFormattedSummaryPdf(
                    fields.filter { !it.value.isNullOrBlank() && it.fieldType != FieldType.STATIC_LABEL && it.value != "delivered" },
                    outputDir,
                    formName
                )
            }
            document = PDDocument.load(inputStream)

            val filledFields = fields.filter { f ->
                !f.value.isNullOrBlank() &&
                f.fieldType != FieldType.STATIC_LABEL &&
                f.value != "delivered"
            }

            // ── Strategy 1: AcroForm ─────────────────────────────────────────
            val acroForm = document.documentCatalog?.acroForm
            if (acroForm != null && acroForm.fields.isNotEmpty()) {
                val filled = fillAcroForm(acroForm, filledFields)
                if (filled > 0) {
                    Log.d(TAG, "AcroForm: filled $filled fields")
                    val out = File(outputDir, "filled_${System.currentTimeMillis()}.pdf")
                    document.save(out)
                    return out
                }
            }

            // ── Strategy 2: Text-position overlay ────────────────────────────
            val extractor = TextBlockExtractor()
            extractor.getText(document)
            val lines = extractor.buildLines()
            Log.d(TAG, "Text extraction: ${lines.size} lines")

            if (lines.isNotEmpty()) {
                for (field in filledFields) {
                    val labelLine = findLabelLine(field.fieldName, lines) ?: continue
                    val page = document.getPage(
                        labelLine.page.coerceIn(0, document.numberOfPages - 1)
                    )
                    overlayValue(document, page, field, labelLine)
                }
                val out = File(outputDir, "filled_${System.currentTimeMillis()}.pdf")
                document.save(out)
                Log.d(TAG, "Text-position overlay saved")
                return out
            }

            // ── Strategy 3: Formatted summary PDF ───────────────────────────
            Log.w(TAG, "Scanned/image PDF — generating formatted summary")
            document.close()
            document = null
            createFormattedSummaryPdf(filledFields, outputDir, formName)

        } catch (e: Exception) {
            Log.e(TAG, "Error filling PDF", e)
            null
        } finally {
            document?.close()
        }
    }

    // ─── Strategy 1: AcroForm Filling ────────────────────────────────────────

    private fun fillAcroForm(
        acroForm: com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm,
        fields: List<FormField>
    ): Int {
        var count = 0
        for (field in fields) {
            // Try matching by field id, then field name, then normalized name
            val pdfField = acroForm.getField(field.id)
                ?: acroForm.getField(field.fieldName)
                ?: acroForm.getField(field.fieldName.replace(" ", "_").lowercase())
                ?: acroForm.fields.firstOrNull { pf ->
                    pf.fullyQualifiedName?.equals(field.fieldName, ignoreCase = true) == true
                }
            if (pdfField != null) {
                try {
                    val displayValue = when (field.fieldType) {
                        FieldType.SIGNATURE -> "Signed"
                        FieldType.CHECKBOX -> if (field.value == "true") "Yes" else "No"
                        else -> field.value ?: ""
                    }
                    pdfField.setValue(displayValue)
                    count++
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set AcroForm field ${field.id}", e)
                }
            }
        }
        return count
    }

    // ─── Strategy 2: Text-Position Overlay ───────────────────────────────────

    data class TextLine(
        val text: String,
        val x: Float,
        val y: Float,      // distance from top of page (PDFTextStripper convention)
        val width: Float,
        val height: Float,
        val page: Int      // 0-indexed
    )

    private data class CharPos(
        val char: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val page: Int
    )

    private inner class TextBlockExtractor : PDFTextStripper() {
        val chars = mutableListOf<CharPos>()
        init { sortByPosition = true }

        override fun processTextPosition(text: TextPosition) {
            val unicode = text.unicode ?: return
            if (unicode.isEmpty()) return
            chars.add(
                CharPos(
                    char = unicode,
                    x = text.xDirAdj,
                    y = text.yDirAdj,
                    w = text.widthDirAdj,
                    h = text.heightDir,
                    page = currentPageNo - 1
                )
            )
        }

        fun buildLines(): List<TextLine> {
            if (chars.isEmpty()) return emptyList()
            val sorted = chars.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
            val groups = mutableListOf<MutableList<CharPos>>()
            var current = mutableListOf<CharPos>()
            for (c in sorted) {
                if (current.isEmpty()) { current.add(c); continue }
                val last = current.last()
                if (c.page == last.page && abs(c.y - last.y) < 4f) {
                    current.add(c)
                } else {
                    groups.add(current)
                    current = mutableListOf(c)
                }
            }
            if (current.isNotEmpty()) groups.add(current)
            return groups.mapNotNull { g ->
                val text = g.joinToString("") { it.char }.trim()
                if (text.isBlank()) null
                else TextLine(
                    text = text,
                    x = g.minOf { it.x },
                    y = g.first().y,
                    width = g.maxOf { it.x + it.w } - g.minOf { it.x },
                    height = g.maxOf { it.h },
                    page = g.first().page
                )
            }
        }
    }

    private fun findLabelLine(fieldLabel: String, lines: List<TextLine>): TextLine? {
        val norm = fieldLabel
            .lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace("—", " ").replace("-", " ")
            .replace("  +".toRegex(), " ")
            .trim().trimEnd(':').trim()

        lines.firstOrNull { it.text.lowercase().contains(norm) }?.let { return it }
        lines.firstOrNull { norm.startsWith(it.text.lowercase().trim().trimEnd(':')) && it.text.length > 4 }
            ?.let { return it }

        val keyWords = norm.split(" ").filter { it.length >= 4 }
        if (keyWords.size >= 2) {
            val best = lines.maxByOrNull { line ->
                val ll = line.text.lowercase()
                keyWords.count { w -> ll.contains(w) }.toDouble() / keyWords.size
            }
            if (best != null) {
                val score = keyWords.count { w -> best.text.lowercase().contains(w) }.toDouble() / keyWords.size
                if (score >= LABEL_MATCH_THRESHOLD) return best
            }
        }
        return null
    }

    private fun overlayValue(document: PDDocument, page: PDPage, field: FormField, label: TextLine) {
        val pageHeight = page.mediaBox.height
        val pageWidth = page.mediaBox.width
        val labelPdfY = pageHeight - label.y
        val labelBottom = labelPdfY - label.height

        if (field.fieldType == FieldType.SIGNATURE) {
            val sigValue = field.value ?: return
            val bitmapFile = if (sigValue.startsWith("signature:"))
                File(sigValue.removePrefix("signature:")).takeIf { it.exists() }
            else null
            if (bitmapFile != null) {
                overlaySignatureBitmap(document, page, bitmapFile, label, labelBottom, pageWidth)
                return
            }
        }

        var stream: PDPageContentStream? = null
        try {
            stream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
            val rightX = label.x + label.width + 6f
            val fitsRight = (rightX + 90f) < (pageWidth - 10f)
            val valueX = if (fitsRight) rightX else label.x
            val valueY = if (fitsRight) labelBottom + 1f else labelBottom - 12f
            val displayValue = formatValue(field).take(80)
            stream.setFont(PDType1Font.HELVETICA, ANSWER_FONT_SIZE)
            stream.setNonStrokingColor(0.07f, 0.25f, 0.70f)
            stream.beginText()
            stream.newLineAtOffset(valueX.coerceIn(10f, pageWidth - 20f), valueY.coerceAtLeast(5f))
            stream.showText(displayValue)
            stream.endText()
        } catch (e: Exception) {
            Log.e(TAG, "Error overlaying ${field.fieldName}", e)
        } finally {
            stream?.close()
        }
    }

    private fun overlaySignatureBitmap(
        document: PDDocument, page: PDPage, bitmapFile: File,
        label: TextLine, labelBottom: Float, pageWidth: Float
    ) {
        var stream: PDPageContentStream? = null
        try {
            val rawBitmap = BitmapFactory.decodeFile(bitmapFile.absolutePath) ?: return
            val maxW = min(200f, pageWidth - label.x - 20f)
            val maxH = 50f
            val scale = minOf(maxW / rawBitmap.width, maxH / rawBitmap.height, 1f)
            val drawW = rawBitmap.width * scale
            val drawH = rawBitmap.height * scale
            val pdImage = LosslessFactory.createFromImage(document, rawBitmap)
            stream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
            val sigX = label.x.coerceIn(10f, pageWidth - drawW - 10f)
            val sigY = (labelBottom - drawH - 4f).coerceAtLeast(5f)
            stream.drawImage(pdImage, sigX, sigY, drawW, drawH)
        } catch (e: Exception) {
            Log.e(TAG, "Signature bitmap overlay failed", e)
        } finally {
            stream?.close()
        }
    }

    private fun formatValue(field: FormField): String {
        val v = field.value ?: return ""
        return when (field.fieldType) {
            FieldType.DATE -> v
            FieldType.SIGNATURE -> "✓ Signed"
            FieldType.CHECKBOX -> if (v == "true" || v == "checked") "☑" else "☐"
            else -> v.replace('\n', ' ').replace('\r', ' ')
        }
    }

    // ─── Strategy 3: Formatted Summary PDF ───────────────────────────────────

    /**
     * Generates a professional patient intake summary — used when the original PDF
     * cannot be overlaid (scanned/image-based forms).
     *
     * Layout: clinic header, generation date, then fields grouped by section in a
     * two-column (label | value) table. Each section gets a shaded header row.
     */
    private fun createFormattedSummaryPdf(
        fields: List<FormField>,
        outputDir: File,
        formName: String
    ): File? {
        val document = PDDocument()
        return try {
            val PAGE = PDRectangle.LETTER  // 612 × 792 pts
            val MARGIN = 48f
            val COL_LABEL = MARGIN
            val COL_VALUE = MARGIN + 220f
            val ROW_H = 18f
            val SECTION_H = 22f

            // Fonts
            val fontBold = PDType1Font.HELVETICA_BOLD
            val fontReg = PDType1Font.HELVETICA
            val fontTitle = PDType1Font.HELVETICA_BOLD

            fun newPage(): Pair<PDPage, PDPageContentStream> {
                val p = PDPage(PAGE)
                document.addPage(p)
                val cs = PDPageContentStream(document, p)
                return p to cs
            }

            var cs = newPage().second
            var y = PAGE.height - MARGIN

            fun checkNewPage(needed: Float) {
                if (y - needed < MARGIN + 20f) {
                    cs.close()
                    cs = newPage().second
                    y = PAGE.height - MARGIN
                }
            }

            fun drawText(text: String, x: Float, yPos: Float, font: PDType1Font, size: Float,
                         r: Float = 0f, g: Float = 0f, b: Float = 0f) {
                cs.setNonStrokingColor(r, g, b)
                cs.setFont(font, size)
                cs.beginText()
                cs.newLineAtOffset(x, yPos)
                // Truncate to prevent overflow
                val maxChars = ((PAGE.width - x - MARGIN) / (size * 0.55f)).toInt().coerceAtLeast(10)
                cs.showText(text.take(maxChars))
                cs.endText()
            }

            fun drawRect(x: Float, yPos: Float, w: Float, h: Float, r: Float, g: Float, b: Float) {
                cs.setNonStrokingColor(r, g, b)
                cs.addRect(x, yPos, w, h)
                cs.fill()
                cs.setNonStrokingColor(0f, 0f, 0f)
            }

            fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
                cs.setStrokingColor(0.8f, 0.8f, 0.8f)
                cs.moveTo(x1, y1)
                cs.lineTo(x2, y2)
                cs.stroke()
                cs.setStrokingColor(0f, 0f, 0f)
            }

            // ── Header ────────────────────────────────────────────────────────
            drawRect(MARGIN, y - 4f, PAGE.width - MARGIN * 2, 36f, 0.10f, 0.35f, 0.65f)
            drawText(formName, MARGIN + 8f, y + 14f, fontTitle, 14f, 1f, 1f, 1f)
            y -= 30f

            val dateStr = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US)
                .format(java.util.Date())
            drawText("Generated: $dateStr", COL_LABEL, y, fontReg, 8f, 0.4f, 0.4f, 0.4f)
            y -= 24f

            // ── Sections ──────────────────────────────────────────────────────
            val sections = linkedMapOf(
                // Sliding Fee Eligibility
                "Applicant Information" to listOf(
                    "full_name", "date_of_birth",
                    "address_street", "address_city", "address_state", "address_zip"
                ),
                "Household & Income" to listOf(
                    "household_size", "number_of_dependents",
                    "income_sources", "monthly_income"
                ),
                "Employment & Insurance" to listOf(
                    "employment_status", "insurance_status"
                ),
                // Coastal Gateway / Medicaid
                "Patient Registration" to listOf(
                    "first_name", "last_name", "date_of_birth", "gender", "preferred_language",
                    "phone_primary", "phone_secondary", "email",
                    "mailing_address_street", "mailing_city", "mailing_state", "mailing_zip",
                    "physical_same_as_mailing",
                    "physical_address_street", "physical_city", "physical_state", "physical_zip",
                    "emergency_contact_name", "emergency_contact_phone", "emergency_contact_relationship",
                    "race", "ethnicity", "marital_status", "employment_status",
                    "has_insurance", "primary_insurance_provider", "primary_insurance_id",
                    "primary_insurance_group", "policyholder_is_self", "policyholder_name",
                    "policyholder_dob", "policyholder_relationship",
                    "has_secondary_insurance", "secondary_insurance_provider",
                    "secondary_insurance_id", "secondary_insurance_group"
                ),
                "Health History" to listOf(
                    "medical_conditions", "family_history_any", "family_history_conditions",
                    "family_history_members", "tobacco_use", "tobacco_type", "tobacco_frequency",
                    "alcohol_use", "alcohol_frequency", "surgeries_any", "surgeries_list",
                    "medications_any", "medications_list", "allergies_any", "allergies_list",
                    "current_provider", "reason_for_visit", "pregnancy_status"
                ),
                "HIPAA & Consents" to listOf(
                    "hipaa_consent", "authorized_phi_contacts_any",
                    "authorized_phi_contact_1_name", "authorized_phi_contact_1_relationship",
                    "authorized_phi_contact_2_name", "authorized_phi_contact_2_relationship",
                    "sliding_fee_acknowledgment", "ghhc_data_sharing", "photo_consent",
                    "filling_for_self", "representative_name", "representative_relationship",
                    "patient_signature"
                )
            )

            val fieldById = fields.associateBy { it.id }

            for ((sectionTitle, fieldIds) in sections) {
                val sectionFields = fieldIds.mapNotNull { id ->
                    fieldById[id]?.takeIf { !it.value.isNullOrBlank() && it.value != "delivered" }
                }
                if (sectionFields.isEmpty()) continue

                checkNewPage(SECTION_H + sectionFields.size * ROW_H + 8f)

                // Section header
                drawRect(MARGIN, y - SECTION_H + 6f, PAGE.width - MARGIN * 2, SECTION_H,
                    0.88f, 0.92f, 0.97f)
                drawText(sectionTitle.uppercase(), MARGIN + 6f, y - 12f, fontBold, 9f,
                    0.10f, 0.30f, 0.60f)
                y -= SECTION_H + 4f

                // Rows
                for (field in sectionFields) {
                    checkNewPage(ROW_H)
                    val label = field.fieldName.trimEnd(':').trim()
                    val value = when (field.fieldType) {
                        FieldType.SIGNATURE -> if (field.value?.startsWith("signature:") == true) "✓ Signed" else field.value ?: ""
                        FieldType.CHECKBOX -> if (field.value == "true") "Yes" else "No"
                        else -> field.value ?: ""
                    }
                    drawText(label, COL_LABEL, y, fontBold, 8f, 0.2f, 0.2f, 0.2f)
                    drawText(value, COL_VALUE, y, fontReg, 8f, 0.07f, 0.25f, 0.70f)
                    drawLine(MARGIN, y - 4f, PAGE.width - MARGIN, y - 4f)
                    y -= ROW_H
                }
                y -= 8f
            }

            // Any remaining fields not in the section map
            val coveredIds = sections.values.flatten().toSet()
            val remaining = fields.filter {
                it.id !in coveredIds &&
                !it.value.isNullOrBlank() &&
                it.fieldType != FieldType.STATIC_LABEL &&
                it.value != "delivered"
            }
            if (remaining.isNotEmpty()) {
                checkNewPage(SECTION_H + remaining.size * ROW_H + 8f)
                drawRect(MARGIN, y - SECTION_H + 6f, PAGE.width - MARGIN * 2, SECTION_H,
                    0.88f, 0.92f, 0.97f)
                drawText("OTHER", MARGIN + 6f, y - 12f, fontBold, 9f, 0.10f, 0.30f, 0.60f)
                y -= SECTION_H + 4f
                for (field in remaining) {
                    checkNewPage(ROW_H)
                    drawText(field.fieldName, COL_LABEL, y, fontBold, 8f, 0.2f, 0.2f, 0.2f)
                    drawText(field.value ?: "", COL_VALUE, y, fontReg, 8f, 0.07f, 0.25f, 0.70f)
                    drawLine(MARGIN, y - 4f, PAGE.width - MARGIN, y - 4f)
                    y -= ROW_H
                }
            }

            // Footer on last page
            cs.setNonStrokingColor(0.6f, 0.6f, 0.6f)
            cs.setFont(fontReg, 7f)
            cs.beginText()
            cs.newLineAtOffset(MARGIN, 30f)
            cs.showText("CONFIDENTIAL — HIPAA Protected Health Information — $formName")
            cs.endText()
            cs.setNonStrokingColor(0f, 0f, 0f)

            cs.close()

            val out = File(outputDir, "filled_${System.currentTimeMillis()}.pdf")
            document.save(out)
            Log.d(TAG, "Formatted summary PDF saved: ${out.absolutePath}")
            out

        } catch (e: Exception) {
            Log.e(TAG, "Error creating formatted summary PDF", e)
            null
        } finally {
            document.close()
        }
    }
}
