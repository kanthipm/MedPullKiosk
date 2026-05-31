package com.medpull.kiosk.data.remote.aws

import com.amazonaws.services.textract.AmazonTextractClient
import com.amazonaws.services.textract.model.*
import com.medpull.kiosk.data.models.BoundingBox
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AWS Textract service for form field extraction
 * Analyzes documents and extracts form fields
 */
@Singleton
class TextractService @Inject constructor(
    private val textractClient: AmazonTextractClient,
    private val s3Service: S3Service
) {

    /**
     * Analyze document and extract form fields
     */
    suspend fun analyzeDocument(s3Key: String, formId: String): TextractResult = withContext(Dispatchers.IO) {
        try {
            // Create S3 object reference
            val s3Object = S3Object().apply {
                bucket = Constants.AWS.S3_BUCKET
                name = s3Key
            }

            val document = Document().withS3Object(s3Object)

            // Analyze document with FORMS feature
            val request = AnalyzeDocumentRequest()
                .withDocument(document)
                .withFeatureTypes("FORMS", "TABLES")

            val result = textractClient.analyzeDocument(request)

            // Extract form fields from result
            val formFields = extractFormFields(result, formId)

            // Extract table structures for downstream table-aware processing
            val tableStructures = extractTableStructures(result)

            // Extract static text blocks (LINE blocks) for translation
            val staticTextBlocks = extractStaticTextBlocks(result)

            TextractResult.Success(formFields, tableStructures, staticTextBlocks)
        } catch (e: Exception) {
            TextractResult.Error(e.message ?: "Textract analysis failed")
        }
    }

    /**
     * Extract form fields from Textract response
     */
    private fun extractFormFields(result: AnalyzeDocumentResult, formId: String): List<FormField> {
        val fields = mutableListOf<FormField>()
        val blocks = result.blocks
        val keyMap = mutableMapOf<String, Block>()
        val valueMap = mutableMapOf<String, Block>()
        val blockMap = blocks.associateBy { it.id }

        // Track which SELECTION_ELEMENT block IDs are consumed by KEY_VALUE_SET pairs
        val consumedSelectionIds = mutableSetOf<String>()

        // Build key-value maps
        blocks.forEach { block ->
            when (block.blockType) {
                "KEY_VALUE_SET" -> {
                    if (block.entityTypes?.contains("KEY") == true) {
                        keyMap[block.id] = block
                    } else if (block.entityTypes?.contains("VALUE") == true) {
                        valueMap[block.id] = block
                    }
                }
            }
        }

        // Process key-value pairs
        keyMap.forEach { (_, keyBlock) ->
            // Find corresponding value block
            val valueId = keyBlock.relationships
                ?.firstOrNull { it.type == "VALUE" }
                ?.ids?.firstOrNull()

            val valueBlock = valueId?.let { valueMap[it] }

            // Extract text from key
            val keyText = getBlockText(keyBlock, blockMap)

            // Check for SELECTION_ELEMENT in value block children
            val selectionResult = valueBlock?.let { detectSelectionElement(it, blockMap) }

            // Track consumed selection elements
            if (selectionResult != null) {
                valueBlock.relationships?.forEach { rel ->
                    if (rel.type == "CHILD") {
                        rel.ids.forEach { childId ->
                            val child = blockMap[childId]
                            if (child?.blockType == "SELECTION_ELEMENT") {
                                consumedSelectionIds.add(childId)
                            }
                        }
                    }
                }
            }

            // Extract text from value (if exists), excluding SELECTION_ELEMENT text
            val valueText = if (selectionResult != null) {
                selectionResult.second
            } else {
                valueBlock?.let { getBlockText(it, blockMap) } ?: ""
            }

            val trimmedKey = keyText.trim()
            val trimmedValue = valueText.trim()

            // --- Junk filtering ---
            if (!isValidFieldKey(trimmedKey)) return@forEach
            if (isStaticPrefilledPair(trimmedKey, trimmedValue, selectionResult != null)) return@forEach

            // Bounding box: use VALUE block for input area, KEY block for label
            val boundingBox = (valueBlock?.geometry?.boundingBox ?: keyBlock.geometry?.boundingBox)?.let {
                BoundingBox(
                    left = it.left,
                    top = it.top,
                    width = it.width,
                    height = it.height,
                    page = (valueBlock?.page ?: keyBlock.page) ?: 1
                )
            }

            // Skip fields with unreasonably small bounding boxes (OCR artifacts)
            if (boundingBox != null && (boundingBox.width < 0.01f || boundingBox.height < 0.005f)) return@forEach

            val labelBoundingBox = keyBlock.geometry?.boundingBox?.let {
                BoundingBox(
                    left = it.left,
                    top = it.top,
                    width = it.width,
                    height = it.height,
                    page = keyBlock.page ?: 1
                )
            }

            // Determine field type: SELECTION_ELEMENT takes priority, then keyword fallback
            val fieldType = if (selectionResult != null) {
                FieldType.CHECKBOX
            } else {
                determineFieldType(trimmedKey)
            }

            val confidence = keyBlock.confidence ?: 0f
            if (confidence < Constants.Pdf.FORM_FIELD_CONFIDENCE_THRESHOLD) return@forEach

            fields.add(
                FormField(
                    id = UUID.randomUUID().toString(),
                    formId = formId,
                    fieldName = trimmedKey,
                    fieldType = fieldType,
                    originalText = trimmedKey,
                    translatedText = null,
                    value = null, // Don't pre-fill — only user-entered values should appear on generated PDF
                    boundingBox = boundingBox,
                    labelBoundingBox = labelBoundingBox,
                    confidence = confidence,
                    required = false,
                    page = keyBlock.page ?: 1
                )
            )
        }

        // --- Recover standalone SELECTION_ELEMENT blocks not in any KEY_VALUE_SET ---
        val lineBlocks = blocks.filter { it.blockType == "LINE" }

        blocks.forEach { block ->
            if (block.blockType != "SELECTION_ELEMENT") return@forEach
            if (block.id in consumedSelectionIds) return@forEach

            val confidence = block.confidence ?: 0f
            if (confidence < Constants.Pdf.FORM_FIELD_CONFIDENCE_THRESHOLD) return@forEach

            val selBB = block.geometry?.boundingBox ?: return@forEach
            val page = block.page ?: 1

            // Find the nearest LINE block on the same page to use as label
            val label = findNearestLineLabel(selBB, page, lineBlocks)

            val isSelected = block.selectionStatus == "SELECTED"

            fields.add(
                FormField(
                    id = UUID.randomUUID().toString(),
                    formId = formId,
                    fieldName = label ?: "Checkbox",
                    fieldType = FieldType.CHECKBOX,
                    originalText = label,
                    translatedText = null,
                    value = if (isSelected) "true" else "",
                    boundingBox = BoundingBox(selBB.left, selBB.top, selBB.width, selBB.height, page),
                    labelBoundingBox = null,
                    confidence = confidence,
                    required = false,
                    page = page
                )
            )
        }

        // --- Extract fillable cells from TABLE blocks ---
        extractTableFields(blocks, blockMap, formId, fields)

        // --- Remove fields inside legal notice / policy sections ---
        val noticeLines = lineBlocks.filter { line ->
            val text = line.text?.uppercase() ?: ""
            text.contains("NOTICE OF") || text.contains("NONDISCRIMINATION") ||
                text.contains("ACCESSIBILITY POLICY")
        }
        if (noticeLines.isNotEmpty()) {
            val noticeTopByPage = noticeLines.groupBy { (it.page ?: 1) }
                .mapValues { (_, lines) ->
                    lines.minOf { it.geometry?.boundingBox?.top ?: 1f }
                }
            fields.removeAll { field ->
                val fieldTop = field.boundingBox?.top ?: field.labelBoundingBox?.top ?: return@removeAll false
                val noticeTop = noticeTopByPage[field.page] ?: return@removeAll false
                fieldTop >= noticeTop
            }
        }

        return deduplicateFields(fields)
    }

    /**
     * Check if a key label looks like a real fillable field label.
     * Rejects OCR noise, single characters, pure punctuation, and pure numbers.
     */
    private fun isValidFieldKey(key: String): Boolean {
        if (key.isBlank()) return false
        // Strip punctuation/whitespace and check what's left
        val alphaContent = key.replace(Regex("[^a-zA-Z]"), "")
        if (alphaContent.length < 2) return false
        // Reject if it's just a number (e.g. "1", "23")
        if (key.trim().toDoubleOrNull() != null) return false
        return true
    }

    /**
     * Detect pre-printed static text that isn't a fillable field.
     * A pair where the value already contains a long pre-printed string is not fillable.
     * Checkboxes are exempt from this check.
     */
    private fun isStaticPrefilledPair(key: String, value: String, isCheckbox: Boolean): Boolean {
        if (isCheckbox) return false
        // Value longer than the key and > 80 chars is likely a paragraph or instructions
        if (value.length > 80) return true
        // Both key and value look like full sentences — probably a description block
        if (key.length > 60 && value.length > 40) return true
        return false
    }

    /**
     * Find the nearest LINE block to a selection element to use as its label.
     * Looks for lines on the same page that are horizontally adjacent (to the left)
     * or immediately above the checkbox.
     */
    private fun findNearestLineLabel(
        selBB: com.amazonaws.services.textract.model.BoundingBox,
        page: Int,
        lineBlocks: List<Block>
    ): String? {
        val selCenterY = selBB.top + selBB.height / 2f
        val selLeft = selBB.left

        var bestLabel: String? = null
        var bestDist = Float.MAX_VALUE

        for (line in lineBlocks) {
            if ((line.page ?: 1) != page) continue
            val lineBB = line.geometry?.boundingBox ?: continue
            val lineCenterY = lineBB.top + lineBB.height / 2f
            val lineRight = lineBB.left + lineBB.width

            // Must be vertically aligned (within 2% of page height)
            val verticalDist = kotlin.math.abs(selCenterY - lineCenterY)
            if (verticalDist > 0.02f) continue

            // Prefer lines to the left of the checkbox, or slightly overlapping
            val horizontalDist = selLeft - lineRight
            if (horizontalDist < -0.05f) continue // line is too far to the right
            if (horizontalDist > 0.15f) continue  // line is too far to the left

            val dist = verticalDist + kotlin.math.abs(horizontalDist)
            if (dist < bestDist) {
                bestDist = dist
                bestLabel = line.text
            }
        }

        // Fallback: check lines immediately above (within 3% vertically, horizontally overlapping)
        if (bestLabel == null) {
            for (line in lineBlocks) {
                if ((line.page ?: 1) != page) continue
                val lineBB = line.geometry?.boundingBox ?: continue
                val lineBottom = lineBB.top + lineBB.height

                val vertGap = selBB.top - lineBottom
                if (vertGap < 0f || vertGap > 0.03f) continue

                // Horizontally overlapping
                val overlapLeft = maxOf(selBB.left, lineBB.left)
                val overlapRight = minOf(selBB.left + selBB.width, lineBB.left + lineBB.width)
                if (overlapRight <= overlapLeft) continue

                val dist = vertGap
                if (dist < bestDist) {
                    bestDist = dist
                    bestLabel = line.text
                }
            }
        }

        return bestLabel?.trim()?.takeIf { it.length >= 2 }
    }

    /**
     * Remove duplicate/overlapping fields. When two fields occupy nearly the same
     * bounding box region on the same page, keep the one with higher confidence.
     */
    private fun deduplicateFields(fields: List<FormField>): List<FormField> {
        if (fields.size <= 1) return fields

        val result = mutableListOf<FormField>()

        for (field in fields.sortedByDescending { it.confidence }) {
            val dominated = result.any { existing ->
                existing.page == field.page && boxOverlap(existing.boundingBox, field.boundingBox) > 0.5f
            }
            if (!dominated) {
                result.add(field)
            }
        }

        return result
    }

    /**
     * Compute intersection-over-union between two bounding boxes.
     * Returns 0 if either is null.
     */
    private fun boxOverlap(a: BoundingBox?, b: BoundingBox?): Float {
        if (a == null || b == null) return 0f
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.left + a.width, b.left + b.width)
        val y2 = minOf(a.top + a.height, b.top + b.height)
        if (x2 <= x1 || y2 <= y1) return 0f
        val intersection = (x2 - x1) * (y2 - y1)
        val areaA = a.width * a.height
        val areaB = b.width * b.height
        val union = areaA + areaB - intersection
        return if (union > 0f) intersection / union else 0f
    }

    /**
     * Extract fillable fields from TABLE blocks.
     * For each table, identifies header row or left-column labels, then creates
     * FormField entries for empty/fillable data cells.
     */
    private fun extractTableFields(
        blocks: List<Block>,
        blockMap: Map<String, Block>,
        formId: String,
        fields: MutableList<FormField>
    ) {
        // Collect all TABLE blocks
        val tableBlocks = blocks.filter { it.blockType == "TABLE" }

        for (tableBlock in tableBlocks) {
            val page = tableBlock.page ?: 1

            // Collect CELL children
            val cellBlocks = mutableListOf<Block>()
            tableBlock.relationships?.forEach { rel ->
                if (rel.type == "CHILD") {
                    rel.ids.forEach { cellId ->
                        blockMap[cellId]?.let { cell ->
                            if (cell.blockType == "CELL") cellBlocks.add(cell)
                        }
                    }
                }
            }
            if (cellBlocks.isEmpty()) continue

            // Build grid: (row, col) -> cell
            val grid = mutableMapOf<Pair<Int, Int>, Block>()
            var maxRow = 0
            var maxCol = 0
            for (cell in cellBlocks) {
                val row = cell.rowIndex ?: continue
                val col = cell.columnIndex ?: continue
                grid[Pair(row, col)] = cell
                if (row > maxRow) maxRow = row
                if (col > maxCol) maxCol = col
            }
            if (maxRow < 1 || maxCol < 1) continue

            // Find header row (handles single-row and two-row headers)
            val headerResult = findHeaderRow(grid, maxRow, maxCol, blockMap)
            val headerTexts = headerResult?.second ?: emptyList()
            val dataStartRow = if (headerResult != null) headerResult.first + 1 else -1

            // Also check if column 1 has labels (common in medical forms: label | value layout)
            val col1Texts = (1..maxRow).mapNotNull { row ->
                grid[Pair(row, 1)]?.let { getBlockText(it, blockMap).trim() }
            }
            val hasLabelColumn = col1Texts.count { it.isNotBlank() } > maxRow / 2

            if (headerResult != null) {
                // Process data rows (after header) with header labels
                for (row in dataStartRow..maxRow) {
                    for (col in 1..maxCol) {
                        val cell = grid[Pair(row, col)] ?: continue
                        val cellText = getBlockText(cell, blockMap).trim()
                        val headerLabel = headerTexts.getOrNull(col - 1) ?: continue

                        // Check for selection element inside cell
                        val selResult = detectSelectionElement(cell, blockMap)
                        val cellBB = cell.geometry?.boundingBox ?: continue
                        val confidence = cell.confidence ?: 0f
                        if (confidence < 50f) continue // lower threshold for table cells

                        val bb = BoundingBox(cellBB.left, cellBB.top, cellBB.width, cellBB.height, page)

                        if (selResult != null) {
                            // Checkbox inside table cell
                            val isSelected = selResult.second == "true"
                            fields.add(FormField(
                                id = UUID.randomUUID().toString(),
                                formId = formId,
                                fieldName = "$headerLabel (Row $row)",
                                fieldType = FieldType.CHECKBOX,
                                originalText = headerLabel,
                                value = if (isSelected) "true" else "",
                                boundingBox = bb,
                                confidence = confidence,
                                page = page
                            ))
                        } else if (cellText.isBlank() || isPlaceholderText(cellText)) {
                            // Empty or placeholder cell — fillable
                            fields.add(FormField(
                                id = UUID.randomUUID().toString(),
                                formId = formId,
                                fieldName = if (maxRow > 2) "$headerLabel (Row $row)" else headerLabel,
                                fieldType = determineFieldType(headerLabel),
                                originalText = headerLabel,
                                value = null, // Don't pre-fill — only user-entered values should appear on generated PDF
                                boundingBox = bb,
                                confidence = confidence,
                                page = page
                            ))
                        }
                    }
                }
            } else if (hasLabelColumn && maxCol >= 2) {
                // Label-value table: column 1 = labels, column 2+ = values
                for (row in 1..maxRow) {
                    val labelCell = grid[Pair(row, 1)] ?: continue
                    val labelText = getBlockText(labelCell, blockMap).trim()
                    if (labelText.isBlank() || !isValidFieldKey(labelText)) continue

                    val labelBB = labelCell.geometry?.boundingBox?.let {
                        BoundingBox(it.left, it.top, it.width, it.height, page)
                    }

                    for (col in 2..maxCol) {
                        val valueCell = grid[Pair(row, col)] ?: continue
                        val cellText = getBlockText(valueCell, blockMap).trim()
                        val selResult = detectSelectionElement(valueCell, blockMap)
                        val cellBB = valueCell.geometry?.boundingBox ?: continue
                        val confidence = valueCell.confidence ?: 0f
                        if (confidence < 50f) continue

                        val bb = BoundingBox(cellBB.left, cellBB.top, cellBB.width, cellBB.height, page)
                        val fieldName = if (maxCol > 2) {
                            val colHeader = headerTexts.getOrNull(col - 1)
                            if (!colHeader.isNullOrBlank()) "$labelText - $colHeader" else labelText
                        } else {
                            labelText
                        }

                        if (selResult != null) {
                            fields.add(FormField(
                                id = UUID.randomUUID().toString(),
                                formId = formId,
                                fieldName = fieldName,
                                fieldType = FieldType.CHECKBOX,
                                originalText = labelText,
                                value = if (selResult.second == "true") "true" else "",
                                boundingBox = bb,
                                labelBoundingBox = labelBB,
                                confidence = confidence,
                                page = page
                            ))
                        } else if (cellText.isBlank() || isPlaceholderText(cellText)) {
                            fields.add(FormField(
                                id = UUID.randomUUID().toString(),
                                formId = formId,
                                fieldName = fieldName,
                                fieldType = determineFieldType(labelText),
                                originalText = labelText,
                                value = null, // Don't pre-fill — only user-entered values should appear on generated PDF
                                boundingBox = bb,
                                labelBoundingBox = labelBB,
                                confidence = confidence,
                                page = page
                            ))
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the header row in a table grid.
     * Tries row 1 first, then row 2 (for two-row headers like "Enroll In:" / "Dental").
     * If row 1 has partial headers and row 2 fills gaps, merges them.
     * Returns (headerRowIndex, headerTexts) or null if no header found.
     */
    private fun findHeaderRow(
        grid: Map<Pair<Int, Int>, Block>,
        maxRow: Int,
        maxCol: Int,
        blockMap: Map<String, Block>
    ): Pair<Int, List<String>>? {
        // Try row 1 as a complete header
        val row1Cells = (1..maxCol).mapNotNull { col -> grid[Pair(1, col)] }
        val row1Texts = (1..maxCol).map { col ->
            grid[Pair(1, col)]?.let { getBlockText(it, blockMap).trim() } ?: ""
        }
        if (row1Texts.all { it.isNotBlank() } && row1Cells.size == maxCol) {
            return Pair(1, row1Texts)
        }

        // Try row 2 as a complete header (common when row 1 has super-headers)
        if (maxRow >= 2) {
            val row2Cells = (1..maxCol).mapNotNull { col -> grid[Pair(2, col)] }
            val row2Texts = (1..maxCol).map { col ->
                grid[Pair(2, col)]?.let { getBlockText(it, blockMap).trim() } ?: ""
            }
            if (row2Texts.all { it.isNotBlank() } && row2Cells.size == maxCol) {
                return Pair(2, row2Texts)
            }

            // Merge: use row 2 text when available, fall back to row 1 text
            val mergedTexts = (1..maxCol).map { col ->
                val r2 = row2Texts[col - 1]
                val r1 = row1Texts[col - 1]
                when {
                    r2.isNotBlank() && r1.isNotBlank() -> r2 // prefer row 2 (more specific)
                    r2.isNotBlank() -> r2
                    r1.isNotBlank() -> r1
                    else -> ""
                }
            }
            if (mergedTexts.count { it.isNotBlank() } >= maxCol - 1) {
                // Allow up to 1 missing header — fill blank with "Column N"
                val finalTexts = mergedTexts.mapIndexed { idx, text ->
                    text.ifBlank { "Column ${idx + 1}" }
                }
                return Pair(2, finalTexts)
            }
        }

        return null
    }

    /**
     * Check if cell text is just a placeholder (underscores, dashes, dots, "N/A", etc.)
     */
    private fun isPlaceholderText(text: String): Boolean {
        val stripped = text.replace(Regex("[_\\-./\\s]"), "")
        if (stripped.isBlank()) return true
        val lower = text.trim().lowercase()
        return lower in setOf("n/a", "na", "none", "___", "...", "—", "–")
    }

    /**
     * Get text from a block by following CHILD relationships (skips SELECTION_ELEMENT blocks)
     */
    private fun getBlockText(block: Block, blockMap: Map<String, Block>): String {
        val text = StringBuilder()

        block.relationships?.forEach { relationship ->
            if (relationship.type == "CHILD") {
                relationship.ids.forEach { childId ->
                    val childBlock = blockMap[childId]
                    if (childBlock?.blockType == "WORD") {
                        childBlock.text?.let {
                            if (text.isNotEmpty()) text.append(" ")
                            text.append(it)
                        }
                    }
                }
            }
        }

        return text.toString()
    }

    /**
     * Detect SELECTION_ELEMENT in a VALUE block's children.
     * Returns Pair(true, value) if found — value is "true" for SELECTED, "" otherwise.
     * Returns null if no SELECTION_ELEMENT child exists.
     */
    private fun detectSelectionElement(valueBlock: Block, blockMap: Map<String, Block>): Pair<Boolean, String>? {
        valueBlock.relationships?.forEach { relationship ->
            if (relationship.type == "CHILD") {
                relationship.ids.forEach { childId ->
                    val childBlock = blockMap[childId]
                    if (childBlock?.blockType == "SELECTION_ELEMENT") {
                        val isSelected = childBlock.selectionStatus == "SELECTED"
                        return Pair(true, if (isSelected) "true" else "")
                    }
                }
            }
        }
        return null
    }

    /**
     * Determine field type based on key text and value.
     * Only used as a keyword fallback when no SELECTION_ELEMENT was detected.
     */
    private fun determineFieldType(keyText: String): FieldType {
        val lowerKey = keyText.lowercase()

        return when {
            // Date fields
            lowerKey.contains("date") || lowerKey.contains("dob") ||
                lowerKey.contains("birth") || Regex("\\bmm[/\\-]dd[/\\-]yy", RegexOption.IGNORE_CASE).containsMatchIn(keyText) ->
                FieldType.DATE

            // Numeric fields
            lowerKey.contains("phone") || lowerKey.contains("tel") ||
                lowerKey.contains("mobile") || lowerKey.contains("fax") ||
                lowerKey.contains("zip") || lowerKey.contains("postal") ||
                lowerKey.contains("ssn") || lowerKey.contains("social security") ->
                FieldType.NUMBER

            lowerKey.contains("age") || lowerKey.contains("weight") ||
                lowerKey.contains("height") || lowerKey.contains("number of") ||
                lowerKey.contains("# of") || lowerKey.contains("qty") ->
                FieldType.NUMBER

            // Signature fields
            lowerKey.contains("signature") || lowerKey.contains("sign here") ||
                lowerKey.contains("patient sign") || lowerKey.contains("authorized sign") ->
                FieldType.SIGNATURE

            // Checkbox — only match explicit yes/no patterns, not generic "select"
            lowerKey.contains("yes/no") || lowerKey.contains("yes / no") ||
                Regex("^\\s*(yes|no|true|false)\\s*$", RegexOption.IGNORE_CASE).matches(keyText) ->
                FieldType.CHECKBOX

            else -> FieldType.TEXT
        }
    }

    /**
     * Extract table structures with precise cell geometry from Textract TABLE blocks.
     * Returns grid metadata (column boundaries, row boundaries, cell bounding boxes)
     * that downstream processors can use to generate precise FormFields.
     */
    private fun extractTableStructures(result: AnalyzeDocumentResult): List<TextractTableStructure> {
        val blocks = result.blocks
        val blockMap = blocks.associateBy { it.id }
        val tableBlocks = blocks.filter { it.blockType == "TABLE" }
        val structures = mutableListOf<TextractTableStructure>()

        for (tableBlock in tableBlocks) {
            val page = tableBlock.page ?: 1
            val tableBB = tableBlock.geometry?.boundingBox ?: continue

            // Collect CELL children
            val cellBlocks = mutableListOf<Block>()
            tableBlock.relationships?.forEach { rel ->
                if (rel.type == "CHILD") {
                    rel.ids.forEach { cellId ->
                        blockMap[cellId]?.let { cell ->
                            if (cell.blockType == "CELL") cellBlocks.add(cell)
                        }
                    }
                }
            }
            if (cellBlocks.isEmpty()) continue

            // Build grid
            var maxRow = 0
            var maxCol = 0
            val grid = mutableMapOf<Pair<Int, Int>, Block>()
            for (cell in cellBlocks) {
                val row = cell.rowIndex ?: continue
                val col = cell.columnIndex ?: continue
                grid[Pair(row, col)] = cell
                if (row > maxRow) maxRow = row
                if (col > maxCol) maxCol = col
            }
            if (maxRow < 2 || maxCol < 1) continue // Need at least header + 1 data row

            // Find header row (handles single-row and multi-row headers)
            val headerResult = findHeaderRow(grid, maxRow, maxCol, blockMap)
                ?: continue // Skip tables without identifiable headers
            val (headerRowIdx, headerTexts) = headerResult
            val dataStartRow = headerRowIdx + 1

            if (dataStartRow > maxRow) continue // No data rows after header

            // Collect column left edges and widths — try header row cells, then data row cells
            val columnLeftEdges = mutableListOf<Float>()
            val columnWidths = mutableListOf<Float>()
            for (col in 1..maxCol) {
                // Try header row first, then the row below it, then any row with this column
                val cellBB = grid[Pair(headerRowIdx, col)]?.geometry?.boundingBox
                    ?: grid[Pair(dataStartRow, col)]?.geometry?.boundingBox
                    ?: (dataStartRow..maxRow).firstNotNullOfOrNull { r ->
                        grid[Pair(r, col)]?.geometry?.boundingBox
                    }
                if (cellBB != null) {
                    columnLeftEdges.add(cellBB.left)
                    columnWidths.add(cellBB.width)
                } else {
                    val estimatedWidth = tableBB.width / maxCol
                    columnLeftEdges.add(tableBB.left + (col - 1) * estimatedWidth)
                    columnWidths.add(estimatedWidth)
                }
            }

            // Collect row top edges from data rows
            val rowTopEdges = mutableListOf<Float>()
            val rowHeights = mutableListOf<Float>()
            for (row in dataStartRow..maxRow) {
                val firstCell = (1..maxCol).firstNotNullOfOrNull { col ->
                    grid[Pair(row, col)]?.geometry?.boundingBox
                }
                if (firstCell != null) {
                    rowTopEdges.add(firstCell.top)
                    rowHeights.add(firstCell.height)
                }
            }

            val avgRowHeight = if (rowHeights.isNotEmpty()) {
                rowHeights.average().toFloat()
            } else {
                (tableBB.height / maxOf(maxRow - dataStartRow + 1, 1)).toFloat()
            }

            // Build cell info for data rows
            val dataRowCount = maxRow - dataStartRow + 1
            val cells = mutableListOf<TextractCellInfo>()
            for (row in dataStartRow..maxRow) {
                for (col in 1..maxCol) {
                    val cell = grid[Pair(row, col)] ?: continue
                    val cellBB = cell.geometry?.boundingBox ?: continue
                    val cellText = getBlockText(cell, blockMap).trim()
                    val selResult = detectSelectionElement(cell, blockMap)

                    cells.add(
                        TextractCellInfo(
                            row = row - dataStartRow + 1, // 1-indexed data rows
                            col = col,
                            boundingBox = BoundingBox(cellBB.left, cellBB.top, cellBB.width, cellBB.height, page),
                            text = cellText,
                            isCheckbox = selResult != null,
                            isSelected = selResult?.second == "true"
                        )
                    )
                }
            }

            structures.add(
                TextractTableStructure(
                    page = page,
                    tableBoundingBox = BoundingBox(tableBB.left, tableBB.top, tableBB.width, tableBB.height, page),
                    headerTexts = headerTexts,
                    columnLeftEdges = columnLeftEdges,
                    columnWidths = columnWidths,
                    rowTopEdges = rowTopEdges,
                    averageRowHeight = avgRowHeight,
                    detectedRowCount = dataRowCount,
                    cells = cells
                )
            )
        }

        return structures
    }

    /**
     * Extract static text blocks from Textract LINE blocks.
     * These are non-fillable text like section headers, instructions, disclaimers, etc.
     * Filters out very short text, Roman numeral section headers (on colored bars),
     * and legal footer notices.
     */
    private fun extractStaticTextBlocks(result: AnalyzeDocumentResult): List<StaticTextBlock> {
        val blocks = result.blocks
        val lineBlocks = blocks.filter { it.blockType == "LINE" }

        return lineBlocks.mapNotNull { block ->
            val text = block.text?.trim() ?: return@mapNotNull null
            val bb = block.geometry?.boundingBox ?: return@mapNotNull null
            val page = block.page ?: 1

            // Filter out very short text (< 3 chars)
            if (text.length < 3) return@mapNotNull null

            // Filter out Roman numeral section headers on colored bars
            // (ALL CAPS text matching "I. ...", "II. ...", "III. ...", etc.)
            val romanPattern = Regex("^[IVX]+\\.\\s+.*")
            if (romanPattern.matches(text) && text == text.uppercase()) return@mapNotNull null

            // Filter out legal footer notices
            val upperText = text.uppercase()
            if (upperText.contains("NOTICE OF") || upperText.contains("POLICY")) return@mapNotNull null

            StaticTextBlock(
                text = text,
                boundingBox = BoundingBox(bb.left, bb.top, bb.width, bb.height, page),
                page = page
            )
        }
    }

    /**
     * Start asynchronous document analysis (for large documents)
     */
    suspend fun startDocumentAnalysis(s3Key: String): String? = withContext(Dispatchers.IO) {
        try {
            val s3Object = S3Object().apply {
                bucket = Constants.AWS.S3_BUCKET
                name = s3Key
            }

            val request = StartDocumentAnalysisRequest()
                .withDocumentLocation(DocumentLocation().withS3Object(s3Object))
                .withFeatureTypes("FORMS", "TABLES")

            val result = textractClient.startDocumentAnalysis(request)
            result.jobId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get document analysis results (for async jobs)
     */
    suspend fun getDocumentAnalysis(jobId: String, formId: String): TextractResult = withContext(Dispatchers.IO) {
        try {
            val request = GetDocumentAnalysisRequest().withJobId(jobId)
            val result = textractClient.getDocumentAnalysis(request)

            when (result.jobStatus) {
                "SUCCEEDED" -> {
                    val formFields = extractFormFields(
                        AnalyzeDocumentResult().withBlocks(result.blocks),
                        formId
                    )
                    TextractResult.Success(formFields)
                }
                "FAILED" -> TextractResult.Error("Analysis job failed")
                "IN_PROGRESS" -> TextractResult.InProgress
                else -> TextractResult.Error("Unknown job status: ${result.jobStatus}")
            }
        } catch (e: Exception) {
            TextractResult.Error(e.message ?: "Failed to get analysis results")
        }
    }
}

/**
 * Textract result sealed class
 */
sealed class TextractResult {
    data class Success(
        val fields: List<FormField>,
        val tableStructures: List<TextractTableStructure> = emptyList(),
        val staticTextBlocks: List<StaticTextBlock> = emptyList()
    ) : TextractResult()
    object InProgress : TextractResult()
    data class Error(val message: String) : TextractResult()
}

/**
 * Non-fillable text block extracted from Textract LINE blocks.
 * Used for translating static text like section headers, instructions, and disclaimers.
 */
data class StaticTextBlock(
    val text: String,
    val boundingBox: BoundingBox,
    val page: Int
)

/**
 * Extracted table structure from Textract with precise cell geometry.
 */
data class TextractTableStructure(
    val page: Int,
    val tableBoundingBox: BoundingBox,
    val headerTexts: List<String>,
    val columnLeftEdges: List<Float>,
    val columnWidths: List<Float>,
    val rowTopEdges: List<Float>,
    val averageRowHeight: Float,
    val detectedRowCount: Int,
    val cells: List<TextractCellInfo>
)

/**
 * Individual cell info from Textract table extraction.
 */
data class TextractCellInfo(
    val row: Int,
    val col: Int,
    val boundingBox: BoundingBox,
    val text: String,
    val isCheckbox: Boolean,
    val isSelected: Boolean
)
