package com.medpull.kiosk.utils

import android.content.Context
import android.util.Log
import com.medpull.kiosk.data.models.FormField
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF utility class for generating filled PDFs
 */
@Singleton
class PdfUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "PdfUtils"
    }

    init {
        // Initialize PDFBox
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Generate a filled PDF from original PDF and field values
     */
    fun generateFilledPdf(
        originalPdfPath: String,
        fields: List<FormField>,
        outputDir: File
    ): File? {
        var document: PDDocument? = null
        try {
            Log.d(TAG, "Generating filled PDF from: $originalPdfPath")

            // Load original PDF
            val originalFile = File(originalPdfPath)
            if (!originalFile.exists()) {
                Log.e(TAG, "Original PDF not found: $originalPdfPath")
                return null
            }

            document = PDDocument.load(originalFile)

            // Add field values as overlays on each page
            fields.groupBy { it.page }.forEach { (pageNumber, pageFields) ->
                if (pageNumber > 0 && pageNumber <= document.numberOfPages) {
                    val page = document.getPage(pageNumber - 1) // Pages are 0-indexed
                    addFieldValuesToPage(document, page, pageFields)
                }
            }

            // Save to output file
            val timestamp = System.currentTimeMillis()
            val outputFile = File(outputDir, "filled_form_$timestamp.pdf")
            document.save(outputFile)

            Log.d(TAG, "Filled PDF generated: ${outputFile.absolutePath}")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error generating filled PDF", e)
            return null
        } finally {
            document?.close()
        }
    }

    /**
     * Add field values to a PDF page
     */
    private fun addFieldValuesToPage(
        document: PDDocument,
        page: PDPage,
        fields: List<FormField>
    ) {
        var contentStream: PDPageContentStream? = null
        try {
            val stream = PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            )
            contentStream = stream

            stream.setFont(PDType1Font.HELVETICA, 10f)

            for (field in fields) {
                if (!field.value.isNullOrBlank() && field.boundingBox != null) {
                    try {
                        // Calculate position
                        // Note: PDF coordinates start from bottom-left
                        val pageHeight = page.mediaBox.height
                        val x = field.boundingBox.left * page.mediaBox.width
                        val y = pageHeight - (field.boundingBox.top * pageHeight) - 12f

                        // Draw text
                        stream.beginText()
                        stream.newLineAtOffset(x, y)
                        stream.showText(field.value)
                        stream.endText()

                        Log.d(TAG, "Added field value: ${field.fieldName} = ${field.value}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding field: ${field.fieldName}", e)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error adding fields to page", e)
        } finally {
            contentStream?.close()
        }
    }

    /**
     * Create a simple filled PDF (fallback method)
     * Creates a new PDF with field values listed
     */
    fun createSimpleFilledPdf(
        fields: List<FormField>,
        outputDir: File,
        formName: String = "Form"
    ): File? {
        var document: PDDocument? = null
        try {
            Log.d(TAG, "Creating simple filled PDF")

            document = PDDocument()
            val page = PDPage()
            document.addPage(page)

            var contentStream: PDPageContentStream? = null
            try {
                var stream = PDPageContentStream(document, page)
                contentStream = stream
                stream.setFont(PDType1Font.HELVETICA_BOLD, 14f)

                // Title
                stream.beginText()
                stream.newLineAtOffset(50f, 750f)
                stream.showText("$formName - Filled")
                stream.endText()

                // Fields
                stream.setFont(PDType1Font.HELVETICA, 10f)
                var yPosition = 720f

                for (field in fields.filter { !it.value.isNullOrBlank() }) {
                    if (yPosition < 50f) {
                        // Add new page if needed
                        stream.close()
                        val newPage = PDPage()
                        document.addPage(newPage)
                        stream = PDPageContentStream(document, newPage)
                        contentStream = stream
                        stream.setFont(PDType1Font.HELVETICA, 10f)
                        yPosition = 750f
                    }

                    stream.beginText()
                    stream.newLineAtOffset(50f, yPosition)
                    stream.showText("${field.fieldName}: ${field.value}")
                    stream.endText()

                    yPosition -= 20f
                }

            } finally {
                contentStream?.close()
            }

            // Save
            val timestamp = System.currentTimeMillis()
            val outputFile = File(outputDir, "filled_form_simple_$timestamp.pdf")
            document.save(outputFile)

            Log.d(TAG, "Simple filled PDF created: ${outputFile.absolutePath}")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error creating simple PDF", e)
            return null
        } finally {
            document?.close()
        }
    }

    /**
     * Validate PDF file
     */
    fun isValidPdf(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            val document = PDDocument.load(file)
            val isValid = document.numberOfPages > 0
            document.close()
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "PDF validation failed", e)
            false
        }
    }

    /**
     * Get PDF page count
     */
    fun getPdfPageCount(filePath: String): Int {
        return try {
            val file = File(filePath)
            if (!file.exists()) return 0

            val document = PDDocument.load(file)
            val count = document.numberOfPages
            document.close()
            count
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page count", e)
            0
        }
    }
}
