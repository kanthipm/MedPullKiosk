package com.medpull.kiosk.data.repository

import android.util.Log
import com.medpull.kiosk.data.local.dao.FormDao
import com.medpull.kiosk.data.local.dao.FormFieldDao
import com.medpull.kiosk.data.local.dao.PatientCacheDao
import com.medpull.kiosk.data.local.entities.FormEntity
import com.medpull.kiosk.data.local.entities.FormFieldEntity
import com.medpull.kiosk.data.local.entities.PatientCacheEntity
import com.medpull.kiosk.data.local.entities.SyncOperationType
import com.medpull.kiosk.data.models.BoundingBox
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.Form
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.data.remote.ai.ClaudeVisionService
import com.medpull.kiosk.data.remote.ai.FieldMerger
import com.medpull.kiosk.data.remote.ai.PdfPageRenderer
import com.medpull.kiosk.data.remote.ai.TableFieldGenerator
import com.medpull.kiosk.data.remote.ai.VisionPageResult
import com.medpull.kiosk.data.remote.aws.S3Service
import com.medpull.kiosk.data.remote.aws.StaticTextBlock
import com.medpull.kiosk.data.remote.aws.TextractResult
import com.medpull.kiosk.data.remote.aws.TextractService
import com.medpull.kiosk.data.remote.aws.TextractTableStructure
import com.medpull.kiosk.data.remote.aws.TranslationService
import com.medpull.kiosk.data.remote.aws.UploadResult
import com.medpull.kiosk.sync.SyncManager
import com.medpull.kiosk.sync.UploadFormPayload
import com.medpull.kiosk.utils.Constants
import com.medpull.kiosk.utils.NetworkMonitor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for form operations
 * Integrates AWS services with local database
 */
@Singleton
class FormRepository @Inject constructor(
    private val formDao: FormDao,
    private val formFieldDao: FormFieldDao,
    private val patientCacheDao: PatientCacheDao,
    private val s3Service: S3Service,
    private val textractService: TextractService,
    private val networkMonitor: NetworkMonitor,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
    private val claudeVisionService: ClaudeVisionService,
    private val pdfPageRenderer: PdfPageRenderer,
    private val translationService: TranslationService
) {

    companion object {
        private const val TAG = "FormRepository"
    }

    /**
     * Get form by ID with fields
     */
    suspend fun getFormById(formId: String): Form? {
        val formEntity = formDao.getFormById(formId) ?: return null
        val fields = formFieldDao.getFieldsByFormId(formId)
        return formEntity.toDomain(fields)
    }

    /**
     * Get form by ID as Flow
     */
    fun getFormByIdFlow(formId: String): Flow<Form?> {
        return combine(
            formDao.getFormByIdFlow(formId),
            formFieldDao.getFieldsByFormIdFlow(formId)
        ) { form, fields ->
            form?.toDomain(fields)
        }
    }

    /**
     * Get forms by user ID
     */
    suspend fun getFormsByUserId(userId: String): List<Form> {
        val forms = formDao.getFormsByUserId(userId)
        return forms.map { form ->
            val fields = formFieldDao.getFieldsByFormId(form.id)
            form.toDomain(fields)
        }
    }

    /**
     * Get forms by user ID as Flow
     */
    fun getFormsByUserIdFlow(userId: String): Flow<List<Form>> {
        return formDao.getFormsByUserIdFlow(userId).map { forms ->
            forms.map { form ->
                val fields = formFieldDao.getFieldsByFormId(form.id)
                form.toDomain(fields)
            }
        }
    }

    /**
     * Save form
     */
    suspend fun saveForm(form: Form) {
        formDao.insertForm(FormEntity.fromDomain(form))
        if (form.fields.isNotEmpty()) {
            formFieldDao.insertFields(form.fields.map { FormFieldEntity.fromDomain(it) })
        }
    }

    /**
     * Update form status
     */
    suspend fun updateFormStatus(formId: String, status: FormStatus) {
        formDao.updateFormStatus(formId, status.name, System.currentTimeMillis())
    }

    /**
     * Update field value
     */
    suspend fun updateFieldValue(fieldId: String, value: String?) {
        formFieldDao.updateFieldValue(fieldId, value)
    }

    /**
     * Delete form
     */
    suspend fun deleteForm(formId: String) {
        formDao.deleteFormById(formId)
    }

    /**
     * Upload and process form
     * Handles offline mode by queuing for later sync
     */
    suspend fun uploadAndProcessForm(
        file: File,
        userId: String,
        formId: String,
        targetLanguage: String = "en",
        onProgress: ((Float) -> Unit)? = null
    ): FormProcessResult {
        // Check if user is still authenticated
        if (!authRepository.isAuthenticated()) {
            Log.e(TAG, "Upload cancelled: user not authenticated")
            updateFormStatus(formId, FormStatus.ERROR)
            return FormProcessResult.Error("Upload cancelled: Please log in again")
        }

        // Check if online
        if (!networkMonitor.isCurrentlyConnected()) {
            Log.d(TAG, "Offline: queuing form processing for $formId")

            // Queue for later processing
            syncManager.queueOperation(
                operationType = SyncOperationType.UPLOAD_FORM,
                entityId = formId,
                payload = UploadFormPayload(
                    formId = formId,
                    userId = userId,
                    localFilePath = file.absolutePath
                ),
                priority = 1
            )

            // Update form status to pending sync
            updateFormStatus(formId, FormStatus.PENDING_SYNC)

            return FormProcessResult.QueuedForSync(
                "Form will be processed when online"
            )
        }

        // Refresh tokens and ensure credentials provider has valid STS creds
        val credentialError = authRepository.refreshTokensIfNeeded()
        if (credentialError != null) {
            Log.e(TAG, "Failed to refresh tokens for upload: $credentialError")
            updateFormStatus(formId, FormStatus.ERROR)
            return FormProcessResult.Error(credentialError)
        }

        // Upload to S3
        val uploadResult = s3Service.uploadFileSync(file, "forms/", userId)

        return when (uploadResult) {
            is UploadResult.Success -> {
                // Update form with S3 key
                formDao.updateFormS3Key(formId, uploadResult.s3Key, System.currentTimeMillis())

                // Update status to processing
                updateFormStatus(formId, FormStatus.PROCESSING)
                onProgress?.invoke(0.30f)

                // Extract fields with Textract
                val textractResult = textractService.analyzeDocument(uploadResult.s3Key, formId)
                onProgress?.invoke(0.45f)

                when (textractResult) {
                    is TextractResult.Success -> {
                        // Run vision enhancement and translation in parallel
                        val fieldsToSave = coroutineScope {
                            val visionDeferred = async {
                                enhanceWithVision(file, textractResult.fields, formId, textractResult.tableStructures)
                            }
                            val translationDeferred = if (targetLanguage != "en") {
                                async {
                                    translateFieldNames(
                                        textractResult.fields, targetLanguage
                                    )
                                }
                            } else null

                            onProgress?.invoke(0.60f)
                            val enhancedFields = visionDeferred.await()
                            onProgress?.invoke(0.75f)
                            val translationMap = translationDeferred?.await()
                            onProgress?.invoke(0.85f)

                            // Create STATIC_LABEL fields from static text blocks
                            val staticLabelFields = createStaticLabelFields(
                                textractResult.staticTextBlocks, enhancedFields, formId
                            )
                            val allFields = enhancedFields + staticLabelFields

                            onProgress?.invoke(0.90f)

                            if (translationMap != null) {
                                // Apply translations; translate vision-added + static label fields too
                                val extraIds = allFields.map { it.id }.toSet() -
                                    textractResult.fields.map { it.id }.toSet()
                                val fullMap = translationMap.toMutableMap()
                                if (extraIds.isNotEmpty()) {
                                    val extraTexts = allFields
                                        .filter { it.id in extraIds }
                                        .associate { it.id to (it.originalText ?: it.fieldName) }
                                    fullMap.putAll(
                                        translationService.translateFormFields(
                                            extraTexts, targetLanguage
                                        )
                                    )
                                }
                                allFields.map { field ->
                                    val translated = fullMap[field.id]
                                    if (translated != null) field.copy(translatedText = translated)
                                    else field
                                }
                            } else {
                                allFields
                            }
                        }
                        onProgress?.invoke(0.95f)

                        // Save fields to database
                        formFieldDao.insertFields(
                            fieldsToSave.map { FormFieldEntity.fromDomain(it) }
                        )

                        // Update status to ready
                        updateFormStatus(formId, FormStatus.READY)
                        formDao.updateFormStatus(
                            formId,
                            FormStatus.READY.name,
                            System.currentTimeMillis()
                        )

                        FormProcessResult.Success(fieldsToSave)
                    }
                    is TextractResult.Error -> {
                        updateFormStatus(formId, FormStatus.ERROR)
                        FormProcessResult.Error(textractResult.message)
                    }
                    TextractResult.InProgress -> {
                        FormProcessResult.Processing
                    }
                }
            }
            is UploadResult.Error -> {
                updateFormStatus(formId, FormStatus.ERROR)
                FormProcessResult.Error(uploadResult.message)
            }
            is UploadResult.QueuedForSync -> {
                updateFormStatus(formId, FormStatus.PENDING_SYNC)
                FormProcessResult.QueuedForSync(uploadResult.message)
            }
        }
    }

    /**
     * Get fields by form ID
     */
    suspend fun getFormFields(formId: String): List<FormField> {
        return formFieldDao.getFieldsByFormId(formId).map { it.toDomain() }
    }

    /**
     * Update multiple field values
     */
    suspend fun updateFieldValues(fieldValues: Map<String, String>) {
        fieldValues.forEach { (fieldId, value) ->
            formFieldDao.updateFieldValue(fieldId, value)
        }
    }

    /**
     * Translate field names via AWS Translate (no DB writes — returns map of fieldId → translated text).
     */
    private suspend fun translateFieldNames(
        fields: List<FormField>,
        targetLanguage: String
    ): Map<String, String> {
        return try {
            val textsToTranslate = fields.associate { it.id to (it.originalText ?: it.fieldName) }
            translationService.translateFormFields(textsToTranslate, targetLanguage)
        } catch (e: Exception) {
            Log.e(TAG, "Translation during processing failed", e)
            emptyMap()
        }
    }

    /**
     * Create STATIC_LABEL FormFields from static text blocks, filtering out
     * blocks that overlap with existing field labels or input areas.
     */
    private fun createStaticLabelFields(
        staticTextBlocks: List<StaticTextBlock>,
        existingFields: List<FormField>,
        formId: String
    ): List<FormField> {
        // Collect all existing bounding boxes (both label and input)
        val existingBoxes = existingFields.flatMap { field ->
            listOfNotNull(field.labelBoundingBox, field.boundingBox)
        }

        return staticTextBlocks.mapNotNull { block ->
            // Check overlap with any existing field bbox (IoU > 0.3)
            val overlaps = existingBoxes.any { existing ->
                existing.page == block.boundingBox.page &&
                    boxIoU(existing, block.boundingBox) > 0.3f
            }
            if (overlaps) return@mapNotNull null

            FormField(
                id = UUID.randomUUID().toString(),
                formId = formId,
                fieldName = block.text,
                fieldType = FieldType.STATIC_LABEL,
                originalText = block.text,
                translatedText = null,
                value = null,
                boundingBox = null,
                labelBoundingBox = block.boundingBox,
                confidence = 1f,
                required = false,
                page = block.page
            )
        }
    }

    /**
     * Compute intersection-over-union between two bounding boxes.
     */
    private fun boxIoU(a: BoundingBox, b: BoundingBox): Float {
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
     * Check if all required fields are filled
     */
    suspend fun areAllRequiredFieldsFilled(formId: String): Boolean {
        val fields = formFieldDao.getFieldsByFormId(formId)
        return fields
            .filter { it.required }
            .all { !it.value.isNullOrBlank() }
    }

    /**
     * Get form completion percentage
     */
    suspend fun getFormCompletionPercentage(formId: String): Float {
        val fields = formFieldDao.getFieldsByFormId(formId)
        if (fields.isEmpty()) return 0f

        val filledCount = fields.count { !it.value.isNullOrBlank() }
        return (filledCount.toFloat() / fields.size) * 100
    }

    /**
     * Enhance Textract fields with Claude Vision post-processing.
     * Renders each PDF page, sends to Claude Vision, then merges results.
     * For tables: uses TableFieldGenerator to combine Textract geometry with Claude's row counts.
     * Falls back to original Textract fields on any failure.
     */
    private suspend fun enhanceWithVision(
        file: File,
        textractFields: List<FormField>,
        formId: String,
        tableStructures: List<TextractTableStructure> = emptyList()
    ): List<FormField> {
        if (!Constants.AI.VISION_ENABLED) {
            Log.d(TAG, "Vision enhancement disabled, using Textract-only fields")
            return textractFields
        }

        return try {
            val pageCount = pdfPageRenderer.getPageCount(file)
            if (pageCount == 0) {
                Log.w(TAG, "Could not get page count, skipping vision enhancement")
                return textractFields
            }

            Log.d(TAG, "Starting vision enhancement for $pageCount pages, ${tableStructures.size} table structures")
            val pageResults = mutableMapOf<Int, VisionPageResult>()

            for (pageIndex in 0 until pageCount) {
                try {
                    val pageBase64 = pdfPageRenderer.renderPageToBase64(file, pageIndex)
                    if (pageBase64 == null) {
                        Log.w(TAG, "Failed to render page $pageIndex, skipping")
                        continue
                    }

                    // Pages are 1-indexed in our data model
                    val pageNumber = pageIndex + 1
                    val pageTextractFields = textractFields.filter { it.page == pageNumber }
                    val pageTableStructures = tableStructures.filter { it.page == pageNumber }

                    val result = claudeVisionService.analyzePage(
                        pageBase64 = pageBase64,
                        pageNumber = pageNumber,
                        existingFields = pageTextractFields,
                        tableStructures = pageTableStructures
                    )

                    if (result.fields.isNotEmpty() || result.tables.isNotEmpty() || result.falsePositives.isNotEmpty()) {
                        pageResults[pageNumber] = result
                    }

                    Log.d(TAG, "Page $pageNumber: ${result.fields.size} vision fields, ${result.tables.size} tables, ${result.falsePositives.size} false positives")
                } catch (e: Exception) {
                    Log.e(TAG, "Vision failed for page ${pageIndex + 1}, using Textract-only", e)
                }
            }

            if (pageResults.isEmpty()) {
                Log.d(TAG, "No vision results, using Textract-only fields")
                return textractFields
            }

            // Generate table fields using TableFieldGenerator
            val tableGeneratedFields = mutableListOf<FormField>()
            for ((pageNumber, result) in pageResults) {
                val pageTableStructures = tableStructures.filter { it.page == pageNumber }
                for (tableStructure in pageTableStructures) {
                    // Match this Textract table to the best Claude Vision table by header similarity
                    val matchedVisionTable = matchVisionTable(tableStructure, result.tables)

                    // If Claude identified tables but didn't mention this one, it's likely
                    // an admin/office-use table — skip it
                    if (matchedVisionTable == null && result.tables.isNotEmpty()) {
                        Log.d(TAG, "Skipping unmatched table with headers: ${tableStructure.headerTexts}")
                        continue
                    }

                    val generated = TableFieldGenerator.generate(
                        tableStructure = tableStructure,
                        visionTableInfo = matchedVisionTable,
                        formId = formId,
                        page = pageNumber
                    )
                    tableGeneratedFields.addAll(generated)
                }
            }

            Log.d(TAG, "TableFieldGenerator produced ${tableGeneratedFields.size} table fields")

            val mergedFields = FieldMerger.mergeAllPages(
                textractFields = textractFields,
                pageResults = pageResults,
                formId = formId,
                tableGeneratedFields = tableGeneratedFields
            )
            Log.d(TAG, "Vision enhancement complete: ${textractFields.size} Textract → ${mergedFields.size} merged fields")
            mergedFields
        } catch (e: Exception) {
            Log.e(TAG, "Vision enhancement failed entirely, using Textract-only fields", e)
            textractFields
        }
    }

    /**
     * Match a Textract table structure to the best-matching Claude Vision table info
     * by comparing header texts. Returns null if no table has meaningful header overlap,
     * which signals that Claude didn't identify this table as patient-fillable.
     */
    private fun matchVisionTable(
        tableStructure: TextractTableStructure,
        visionTables: List<com.medpull.kiosk.data.remote.ai.VisionTableInfo>
    ): com.medpull.kiosk.data.remote.ai.VisionTableInfo? {
        if (visionTables.isEmpty()) return null

        val textractHeaders = tableStructure.headerTexts.map { it.lowercase().trim() }.toSet()

        // Find the vision table with the most header overlap, requiring at least 1 match
        return visionTables
            .map { vt ->
                val visionHeaders = vt.headerTexts.map { it.lowercase().trim() }.toSet()
                vt to textractHeaders.intersect(visionHeaders).size
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    // ─── Patient cache (cross-form prefill) ──────────────────────────────────

    suspend fun getPatientCache(userId: String): PatientCacheEntity? =
        patientCacheDao.getByUserId(userId)

    suspend fun savePatientCache(userId: String, fields: List<FormField>) {
        val byId = fields.associateBy { it.id }
        patientCacheDao.upsert(
            PatientCacheEntity(
                userId = userId,
                patientFullName              = byId["patient_full_name"]?.value,
                dateOfBirth                  = byId["date_of_birth"]?.value,
                mailingStreet                = byId["mailing_address_street"]?.value,
                mailingCity                  = byId["mailing_city"]?.value,
                mailingState                 = byId["mailing_state"]?.value,
                mailingZip                   = byId["mailing_zip"]?.value,
                cellPhone                    = byId["cell_phone"]?.value,
                email                        = byId["email"]?.value,
                emergencyContactName         = byId["emergency_contact_name"]?.value,
                emergencyContactPhone        = byId["emergency_contact_phone"]?.value,
                emergencyContactRelationship = byId["emergency_contact_relationship"]?.value
            )
        )
    }
}

/**
 * Form process result sealed class
 */
sealed class FormProcessResult {
    data class Success(val fields: List<FormField>) : FormProcessResult()
    object Processing : FormProcessResult()
    data class Error(val message: String) : FormProcessResult()
    data class QueuedForSync(val message: String) : FormProcessResult()
}
