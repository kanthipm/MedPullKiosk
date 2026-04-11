package com.medpull.kiosk.data.remote.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.medpull.kiosk.BuildConfig
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.remote.aws.TextractTableStructure
import com.medpull.kiosk.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude Vision service for multi-modal form field extraction.
 * Sends page images + Textract context to Claude for enhanced field detection.
 */
@Singleton
class ClaudeVisionService @Inject constructor(
    private val gson: Gson
) {

    companion object {
        private const val TAG = "ClaudeVisionService"

        // Used when Textract detected table structures — Claude returns table metadata only
        private val SYSTEM_PROMPT_WITH_TABLES = """
            You are a medical form field extraction specialist. Your job is to analyze images of medical/dental/insurance form pages and identify ALL fillable fields.

            You must return ONLY valid JSON — no markdown, no explanation, no extra text.

            Focus on:
            - Text input fields (name, address, SSN, etc.)
            - Date fields (MM/DD/YYYY patterns, date labels)
            - Checkboxes (small squares, circles for yes/no, plan selection, etc.)
            - Signature lines
            - Number fields (phone, zip, member ID, group number)
            - Blank lines after "#" or "No." labels are TEXT/NUMBER fields, NOT checkboxes (e.g. "Division #___" and "Prior ID #___" are text inputs)
            - Look for commonly missed fields: "Prior ID #", reference numbers, ID numbers with underlines

            CRITICAL — Tables:
            - For tables, do NOT return individual cell bounding boxes. Instead, return table metadata in the "tables" section.
            - Count the TOTAL number of fillable data rows (empty rows where a user would write), not just rows with text.
            - Identify the type of each column (TEXT, DATE, CHECKBOX, NUMBER, SIGNATURE).
            - If a column header spans sub-columns (e.g. "Enroll In" with "Dental" and "Vision" underneath), list them as sub_columns.

            Do NOT include:
            - Section headers or titles (e.g., "SECTION III: DEPENDENT INFORMATION")
            - Column headers themselves — only describe them in the tables section
            - Instructions or policy language
            - Pre-printed static text that is not editable
            - Logos, page numbers, or decorative elements
            - Labels themselves (only the fillable area next to a label)
            - Employer/admin/office-use fields (group numbers, division numbers, employer name, plan info, contract dates at the top of the form that are NOT filled by the patient)
            - Company/provider header fields (company name, address, phone number, logo area at the very top of the form)

            Flag ALL employer/admin/office-use/company-header fields in the false_positives list if Textract detected them.

            Bounding boxes for non-table fields must be normalized fractions (0.0 to 1.0) of the page dimensions.
        """.trimIndent()

        // Used when Textract did NOT detect table structures — Claude returns cell-level fields
        private val SYSTEM_PROMPT_NO_TABLES = """
            You are a medical form field extraction specialist. Your job is to analyze images of medical/dental/insurance form pages and identify ALL fillable fields.

            You must return ONLY valid JSON — no markdown, no explanation, no extra text.

            Focus on:
            - Text input fields (name, address, SSN, etc.)
            - Date fields (MM/DD/YYYY patterns, date labels)
            - Checkboxes (small squares, circles for yes/no, plan selection, etc.)
            - Signature lines
            - Number fields (phone, zip, member ID, group number)
            - Blank lines after "#" or "No." labels are TEXT/NUMBER fields, NOT checkboxes (e.g. "Division #___" and "Prior ID #___" are text inputs)
            - Look for commonly missed fields: "Prior ID #", reference numbers, ID numbers with underlines

            CRITICAL — Tables:
            - Detect EVERY empty row in data tables, not just the first row
            - If a table has 5 blank rows, return a field for EACH cell in EACH of those 5 rows
            - Name table fields as "Column Header (Row N)" e.g. "First Name (Row 1)", "First Name (Row 2)"
            - Include checkbox columns in tables (e.g. "Enroll In: Dental (Row 1)")
            - Common missed tables: dependent information, medication lists, provider lists

            Do NOT include:
            - Section headers or titles (e.g., "SECTION III: DEPENDENT INFORMATION")
            - Column headers themselves (e.g., "First Name", "Relationship") — only the empty cells below them
            - Instructions or policy language
            - Pre-printed static text that is not editable
            - Logos, page numbers, or decorative elements
            - Labels themselves (only the fillable area next to a label)
            - Employer/admin/office-use fields (group numbers, division numbers, employer name, plan info, contract dates at the top of the form that are NOT filled by the patient)

            Flag employer/admin/office-use fields in the false_positives list if Textract detected them.

            Bounding boxes must be normalized fractions (0.0 to 1.0) of the page dimensions.
        """.trimIndent()
    }

    // Dedicated OkHttpClient with longer timeout for vision calls
    private val visionClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.AI.VISION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Analyze a single page image with Claude Vision.
     * Returns the extracted fields, table metadata, and list of false positive field names from Textract.
     */
    suspend fun analyzePage(
        pageBase64: String,
        pageNumber: Int,
        existingFields: List<FormField>,
        tableStructures: List<TextractTableStructure> = emptyList()
    ): VisionPageResult = withContext(Dispatchers.IO) {
        try {
            val textractFieldsSummary = if (existingFields.isNotEmpty()) {
                existingFields.joinToString("\n") { field ->
                    "- \"${field.fieldName}\" (${field.fieldType.name}, page ${field.page}, " +
                        "bbox: left=${field.boundingBox?.left ?: "?"}, top=${field.boundingBox?.top ?: "?"}, " +
                        "w=${field.boundingBox?.width ?: "?"}, h=${field.boundingBox?.height ?: "?"})"
                }
            } else {
                "(none detected)"
            }

            val hasTableStructures = tableStructures.isNotEmpty()
            val systemPrompt = if (hasTableStructures) SYSTEM_PROMPT_WITH_TABLES else SYSTEM_PROMPT_NO_TABLES

            val userPrompt = if (hasTableStructures) {
                val tableContextSummary = tableStructures.joinToString("\n") { table ->
                    "- Table with headers: [${table.headerTexts.joinToString(", ")}], " +
                        "Textract detected ${table.detectedRowCount} data rows"
                }
                """
                Analyze this medical form page (page $pageNumber) and identify ALL fillable fields.

                Textract already detected these fields on this page:
                $textractFieldsSummary

                Textract detected these tables on this page:
                $tableContextSummary

                Please:
                1. For NON-TABLE fields: Identify any fillable fields that Textract MISSED (signature lines, standalone checkboxes, text inputs).
                   Return bounding boxes as normalized 0-1 fractions of page width/height.
                2. For TABLES: Look at each table visually and tell me:
                   - How many TOTAL fillable data rows exist (count ALL empty rows, not just ones with text)
                   - What type each column is (TEXT, NUMBER, DATE, CHECKBOX, SIGNATURE)
                   - If any column header spans sub-columns (e.g. "Enroll In" → Dental, Vision), list the sub-columns
                   Do NOT return bounding boxes for table cells — just counts and types.
                3. Flag any Textract fields that are NOT actually fillable (static text, headers, instructions)

                Return ONLY this JSON structure:
                {
                  "fields": [
                    {
                      "field_name": "descriptive name",
                      "field_type": "TEXT|NUMBER|DATE|CHECKBOX|SIGNATURE",
                      "bounding_box": {"left": 0.0, "top": 0.0, "width": 0.0, "height": 0.0},
                      "required": false,
                      "is_fillable": true,
                      "section": "section name or null"
                    }
                  ],
                  "tables": [
                    {
                      "header_texts": ["First Name", "Last Name", "DOB"],
                      "actual_data_row_count": 5,
                      "column_types": ["TEXT", "TEXT", "DATE"],
                      "sub_columns": [
                        {"parent_header": "Enroll In", "sub_headers": ["Dental", "Vision"]}
                      ]
                    }
                  ],
                  "false_positives": ["field name from Textract that is NOT fillable"]
                }
                """.trimIndent()
            } else {
                // No Textract table structures — use original cell-level detection
                """
                Analyze this medical form page (page $pageNumber) and identify ALL fillable fields.

                Textract already detected these fields on this page:
                $textractFieldsSummary

                Please:
                1. Identify any fillable fields that Textract MISSED — pay special attention to:
                   - EVERY empty row in data tables (if a table has 5 blank rows, list each cell in each row)
                   - Checkbox columns in tables (e.g. enrollment checkboxes on the right side)
                   - Signature lines at the bottom
                2. Flag any Textract fields above that are NOT actually fillable (static text, headers, instructions, policy language)
                3. Return bounding boxes as normalized 0-1 fractions of page width/height

                Return ONLY this JSON structure:
                {
                  "fields": [
                    {
                      "field_name": "descriptive name of the field",
                      "field_type": "TEXT|NUMBER|DATE|CHECKBOX|SIGNATURE",
                      "bounding_box": {"left": 0.0, "top": 0.0, "width": 0.0, "height": 0.0},
                      "required": false,
                      "is_fillable": true,
                      "section": "section name or null"
                    }
                  ],
                  "false_positives": ["field name from Textract that is NOT fillable"]
                }
                """.trimIndent()
            }

            Log.d(TAG, "Page $pageNumber: sending ${pageBase64.length / 1024}KB base64, ${existingFields.size} existing fields")

            // Build multi-modal request with image + text content blocks
            val contentBlocks = listOf(
                mapOf(
                    "type" to "image",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to "image/jpeg",
                        "data" to pageBase64
                    )
                ),
                mapOf(
                    "type" to "text",
                    "text" to userPrompt
                )
            )

            val requestBody = mapOf(
                "model" to "claude-haiku-4-5-20251001", // disabled — VISION_ENABLED = false
                "max_tokens" to Constants.AI.VISION_MAX_TOKENS,
                "system" to systemPrompt,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to contentBlocks
                    )
                )
            )

            val bodyJson = gson.toJson(requestBody)
            Log.d(TAG, "Request body size: ${bodyJson.length / 1024}KB")

            val request = Request.Builder()
                .url(Constants.AI.CLAUDE_API_URL)
                .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
                .addHeader("anthropic-version", Constants.AI.CLAUDE_API_VERSION)
                .addHeader("content-type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = visionClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Vision API error ${response.code} for page $pageNumber: $errorBody")
                return@withContext VisionPageResult.empty()
            }

            val responseJson = response.body?.string()
            if (responseJson.isNullOrBlank()) {
                Log.e(TAG, "Empty response body for page $pageNumber")
                return@withContext VisionPageResult.empty()
            }

            val claudeResponse = gson.fromJson(responseJson, ClaudeResponse::class.java)
            val text = claudeResponse.content?.firstOrNull()?.text
            val truncated = claudeResponse.stopReason == "max_tokens"

            if (text.isNullOrBlank()) {
                Log.e(TAG, "No text in Claude response for page $pageNumber. Raw: ${responseJson.take(500)}")
                return@withContext VisionPageResult.empty()
            }

            if (truncated) {
                Log.w(TAG, "Page $pageNumber response was TRUNCATED (max_tokens reached). Attempting repair.")
            }
            Log.d(TAG, "Page $pageNumber raw response (${text.length} chars, truncated=$truncated): ${text.take(200)}")
            parseVisionResponse(text, pageNumber, truncated)
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed for page $pageNumber", e)
            VisionPageResult.empty()
        }
    }

    /**
     * Parse Claude's JSON response into structured fields.
     * If truncated, attempts to repair the JSON by closing open structures.
     */
    private fun parseVisionResponse(responseText: String, pageNumber: Int, truncated: Boolean): VisionPageResult {
        // Extract JSON object — find first { and last }
        val jsonStart = responseText.indexOf('{')
        if (jsonStart < 0) {
            Log.e(TAG, "No JSON found in response for page $pageNumber: ${responseText.take(300)}")
            return VisionPageResult.empty()
        }

        val jsonEnd = responseText.lastIndexOf('}')
        val rawJson = if (jsonEnd > jsonStart) {
            responseText.substring(jsonStart, jsonEnd + 1)
        } else {
            responseText.substring(jsonStart)
        }

        // Try parsing directly first
        val jsonText = if (!truncated) {
            rawJson
        } else {
            // Repair truncated JSON: salvage complete field objects
            repairTruncatedJson(rawJson) ?: run {
                Log.e(TAG, "Could not repair truncated JSON for page $pageNumber")
                return VisionPageResult.empty()
            }
        }

        return try {
            val responseType = object : TypeToken<VisionResponse>() {}.type
            val visionResponse: VisionResponse = gson.fromJson(jsonText, responseType)
            toVisionPageResult(visionResponse, pageNumber)
        } catch (e: Exception) {
            if (!truncated) {
                // Wasn't flagged as truncated but still failed — try repair anyway
                Log.w(TAG, "Parse failed, attempting repair for page $pageNumber")
                val repaired = repairTruncatedJson(rawJson)
                if (repaired != null) {
                    try {
                        val responseType = object : TypeToken<VisionResponse>() {}.type
                        val visionResponse: VisionResponse = gson.fromJson(repaired, responseType)
                        return toVisionPageResult(visionResponse, pageNumber)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Repair also failed for page $pageNumber", e2)
                    }
                }
            }
            Log.e(TAG, "Failed to parse vision response for page $pageNumber: ${responseText.take(300)}", e)
            VisionPageResult.empty()
        }
    }

    private fun toVisionPageResult(visionResponse: VisionResponse, pageNumber: Int): VisionPageResult {
        val fields = visionResponse.fields?.map { vf ->
            ClaudeVisionField(
                fieldName = vf.fieldName ?: "Unknown",
                fieldType = vf.fieldType ?: "TEXT",
                boundingBox = vf.boundingBox,
                required = vf.required ?: false,
                isFillable = vf.isFillable ?: true,
                section = vf.section,
                page = pageNumber
            )
        } ?: emptyList()

        val tables = visionResponse.tables?.map { vt ->
            VisionTableInfo(
                headerTexts = vt.headerTexts ?: emptyList(),
                actualDataRowCount = vt.actualDataRowCount ?: 0,
                columnTypes = vt.columnTypes ?: emptyList(),
                subColumns = vt.subColumns?.map { sc ->
                    VisionSubColumn(
                        parentHeader = sc.parentHeader ?: "",
                        subHeaders = sc.subHeaders ?: emptyList()
                    )
                } ?: emptyList()
            )
        } ?: emptyList()

        val falsePositives = visionResponse.falsePositives ?: emptyList()

        Log.d(TAG, "Page $pageNumber parsed: ${fields.size} vision fields, ${tables.size} tables, ${falsePositives.size} false positives")
        return VisionPageResult(fields = fields, tables = tables, falsePositives = falsePositives)
    }

    /**
     * Repair truncated JSON by finding the last complete field object in the
     * "fields" array and closing the structure with an empty false_positives.
     */
    private fun repairTruncatedJson(json: String): String? {
        val arrayStart = json.indexOf('[')
        if (arrayStart < 0) return null

        // Walk through the fields array tracking brace depth to find complete objects
        var depth = 0
        var lastCompleteObjectEnd = -1
        var inString = false
        var escape = false

        for (i in (arrayStart + 1) until json.length) {
            val c = json[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue

            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        lastCompleteObjectEnd = i
                    }
                }
            }
        }

        if (lastCompleteObjectEnd < 0) return null

        // Rebuild: prefix through last complete object, then close array + root
        val prefix = json.substring(0, lastCompleteObjectEnd + 1)
        val repaired = "$prefix], \"tables\": [], \"false_positives\": []}"
        Log.d(TAG, "Repaired truncated JSON: kept ${prefix.length} chars, closed structure")
        return repaired
    }
}

// --- Vision response models ---

data class VisionResponse(
    val fields: List<VisionFieldJson>?,
    val tables: List<VisionTableJson>?,
    @SerializedName("false_positives")
    val falsePositives: List<String>?
)

data class VisionTableJson(
    @SerializedName("header_texts")
    val headerTexts: List<String>?,
    @SerializedName("actual_data_row_count")
    val actualDataRowCount: Int?,
    @SerializedName("column_types")
    val columnTypes: List<String>?,
    @SerializedName("sub_columns")
    val subColumns: List<VisionSubColumnJson>?
)

data class VisionSubColumnJson(
    @SerializedName("parent_header")
    val parentHeader: String?,
    @SerializedName("sub_headers")
    val subHeaders: List<String>?
)

data class VisionFieldJson(
    @SerializedName("field_name")
    val fieldName: String?,
    @SerializedName("field_type")
    val fieldType: String?,
    @SerializedName("bounding_box")
    val boundingBox: VisionBoundingBox?,
    val required: Boolean?,
    @SerializedName("is_fillable")
    val isFillable: Boolean?,
    val section: String?
)

data class VisionBoundingBox(
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

data class ClaudeVisionField(
    val fieldName: String,
    val fieldType: String,
    val boundingBox: VisionBoundingBox?,
    val required: Boolean,
    val isFillable: Boolean,
    val section: String?,
    val page: Int
)

data class VisionPageResult(
    val fields: List<ClaudeVisionField>,
    val tables: List<VisionTableInfo> = emptyList(),
    val falsePositives: List<String>
) {
    companion object {
        fun empty() = VisionPageResult(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Claude Vision's assessment of a table: row count and column types.
 * No bounding boxes — those come from Textract.
 */
data class VisionTableInfo(
    val headerTexts: List<String>,
    val actualDataRowCount: Int,
    val columnTypes: List<String>,
    val subColumns: List<VisionSubColumn> = emptyList()
)

data class VisionSubColumn(
    val parentHeader: String,
    val subHeaders: List<String>
)
