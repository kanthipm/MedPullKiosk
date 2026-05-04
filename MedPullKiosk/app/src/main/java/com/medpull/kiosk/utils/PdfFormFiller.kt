package com.medpull.kiosk.utils

import android.content.Context
import android.util.Log
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Fills the original form PDF with patient answers by:
 *   1. Extracting text positions from the PDF using PDFBox
 *   2. Matching each field label to its location on the page
 *   3. Overlaying the patient's answer in blue text near the label
 *
 * Falls back to createSimpleFilledPdf() if text extraction yields nothing
 * (e.g. scanned/image-based PDFs).
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
        private const val LABEL_MATCH_THRESHOLD = 0.55 // fraction of key words that must match
    }

    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Generate a filled PDF for the Coastal Gateway form.
     * Returns the filled File, or null on failure.
     */
    fun fillCoastalGatewayForm(fields: List<FormField>, outputDir: File): File? {
        return fillForm(fields, COASTAL_GATEWAY_PDF, "Coastal Gateway — Patient Intake", outputDir)
    }

    /**
     * Generic form filler: loads [pdfAssetPath] from assets, overlays answers, saves to [outputDir].
     */
    fun fillForm(
        fields: List<FormField>,
        pdfAssetPath: String,
        formName: String,
        outputDir: File
    ): File? {
        var document: PDDocument? = null
        return try {
            outputDir.mkdirs()

            val inputStream = context.assets.open(pdfAssetPath)
            document = PDDocument.load(inputStream)

            // Extract all text blocks with page/position info
            val extractor = TextBlockExtractor()
            extractor.getText(document)
            val lines = extractor.buildLines()

            Log.d(TAG, "Extracted ${lines.size} text lines from PDF")

            if (lines.isEmpty()) {
                // Scanned/image PDF — fall back to simple list format
                Log.w(TAG, "No text extracted — falling back to simple PDF")
                document.close()
                document = null
                return pdfUtils.createSimpleFilledPdf(fields, outputDir, formName)
            }

            val filledFields = fields.filter { f ->
                !f.value.isNullOrBlank() &&
                f.fieldType != FieldType.STATIC_LABEL &&
                f.value != "delivered"
            }

            Log.d(TAG, "Overlaying ${filledFields.size} filled fields")

            for (field in filledFields) {
                val labelLine = findLabelLine(field.fieldName, lines) ?: continue
                val page = document.getPage(labelLine.page.coerceIn(0, document.numberOfPages - 1))
                overlayValue(document, page, field, labelLine)
            }

            val outputFile = File(outputDir, "filled_${System.currentTimeMillis()}.pdf")
            document.save(outputFile)
            Log.d(TAG, "Filled PDF saved: ${outputFile.absolutePath}")
            outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error filling PDF", e)
            null
        } finally {
            document?.close()
        }
    }

    // ─── Text Extraction ──────────────────────────────────────────────────────

    data class TextLine(
        val text: String,
        val x: Float,      // left edge in PDF points (origin top-left, per PDFTextStripper)
        val y: Float,      // top edge in PDF points (origin top-left, per PDFTextStripper)
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

        init {
            sortByPosition = true
        }

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
                    page = currentPageNo - 1  // currentPageNo is 1-indexed
                )
            )
        }

        /** Group individual characters into text lines. */
        fun buildLines(): List<TextLine> {
            if (chars.isEmpty()) return emptyList()

            val sorted = chars.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
            val lines = mutableListOf<MutableList<CharPos>>()
            var current = mutableListOf<CharPos>()

            for (c in sorted) {
                if (current.isEmpty()) {
                    current.add(c)
                    continue
                }
                val last = current.last()
                val sameLine = c.page == last.page && abs(c.y - last.y) < 4f
                if (sameLine) {
                    current.add(c)
                } else {
                    lines.add(current)
                    current = mutableListOf(c)
                }
            }
            if (current.isNotEmpty()) lines.add(current)

            return lines.mapNotNull { lineChars ->
                val text = lineChars.joinToString("") { it.char }.trim()
                if (text.isBlank()) return@mapNotNull null
                TextLine(
                    text = text,
                    x = lineChars.minOf { it.x },
                    y = lineChars.first().y,
                    width = lineChars.maxOf { it.x + it.w } - lineChars.minOf { it.x },
                    height = lineChars.maxOf { it.h },
                    page = lineChars.first().page
                )
            }
        }
    }

    // ─── Label Matching ───────────────────────────────────────────────────────

    private fun findLabelLine(fieldLabel: String, lines: List<TextLine>): TextLine? {
        // Normalize the label: lowercase, drop trailing colon, drop parenthetical hints
        val norm = fieldLabel
            .lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace("—", " ")
            .replace("  +".toRegex(), " ")
            .trim()
            .trimEnd(':')
            .trim()

        // 1. Direct substring match
        lines.firstOrNull { it.text.lowercase().contains(norm) }?.let { return it }

        // 2. The PDF label may be truncated — check if norm starts with the line text
        lines.firstOrNull { norm.startsWith(it.text.lowercase().trim().trimEnd(':')) && it.text.length > 4 }
            ?.let { return it }

        // 3. Word-overlap match: require LABEL_MATCH_THRESHOLD of key words to appear
        val keyWords = norm.split(" ").filter { it.length >= 4 }
        if (keyWords.size >= 2) {
            val best = lines.maxByOrNull { line ->
                val lineLower = line.text.lowercase()
                keyWords.count { w -> lineLower.contains(w) }.toDouble() / keyWords.size
            }
            if (best != null) {
                val lineLower = best.text.lowercase()
                val score = keyWords.count { w -> lineLower.contains(w) }.toDouble() / keyWords.size
                if (score >= LABEL_MATCH_THRESHOLD) return best
            }
        }

        return null
    }

    // ─── Value Overlay ────────────────────────────────────────────────────────

    private fun overlayValue(
        document: PDDocument,
        page: PDPage,
        field: FormField,
        label: TextLine
    ) {
        var stream: PDPageContentStream? = null
        try {
            stream = PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true, true
            )

            val pageHeight = page.mediaBox.height
            val pageWidth = page.mediaBox.width

            // PDFTextStripper y = distance from top of page.
            // PDFBox coordinate origin = bottom-left, so pdfY = pageHeight - stripperY.
            val labelPdfY = pageHeight - label.y
            val labelBottom = labelPdfY - label.height

            // Try to place value inline (to the right of the label)
            val rightX = label.x + label.width + 6f
            val fitsRight = (rightX + 90f) < (pageWidth - 10f)

            val valueX = if (fitsRight) rightX else label.x
            val valueY = if (fitsRight) labelBottom + 1f else labelBottom - 12f

            val displayValue = formatValue(field).take(80)

            stream.setFont(PDType1Font.HELVETICA, ANSWER_FONT_SIZE)
            // Blue-ink color for answers
            stream.setNonStrokingColor(0.07f, 0.25f, 0.70f)
            stream.beginText()
            stream.newLineAtOffset(valueX.coerceIn(10f, pageWidth - 20f), valueY.coerceAtLeast(5f))
            stream.showText(displayValue)
            stream.endText()

        } catch (e: Exception) {
            Log.e(TAG, "Error overlaying value for ${field.fieldName}", e)
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
}
