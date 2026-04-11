package com.medpull.kiosk.data.models

/**
 * Form domain model
 */
data class Form(
    val id: String,
    val userId: String,
    val fileName: String,
    val originalFileUri: String,
    val s3Key: String? = null,
    val status: FormStatus = FormStatus.UPLOADED,
    val fields: List<FormField> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null,
    val processedAt: Long? = null,
    val exportedAt: Long? = null
)

/**
 * Form status enum
 */
enum class FormStatus {
    UPLOADING,      // Being uploaded
    UPLOADED,       // Uploaded to local storage
    PENDING_SYNC,   // Queued for sync when online
    PROCESSING,     // Being processed by Textract
    READY,          // Ready for filling
    IN_PROGRESS,    // Being filled out
    COMPLETED,      // Filled and ready to export
    EXPORTED,       // Exported to S3/local
    ERROR           // Processing error
}

/**
 * Individual form field
 */
data class FormField(
    val id: String,
    val formId: String,
    val fieldName: String,
    val fieldType: FieldType,
    val originalText: String? = null,
    val translatedText: String? = null,
    val value: String? = null,
    val boundingBox: BoundingBox? = null,
    val labelBoundingBox: BoundingBox? = null,
    val confidence: Float = 0f,
    val required: Boolean = false,
    val page: Int = 1,
    val options: List<String> = emptyList(),   // For RADIO/DROPDOWN — rendered as choice chips
    val description: String? = null             // From schema ai_note — shown below question text
)

/**
 * Field type enum
 */
enum class FieldType {
    TEXT,
    NUMBER,
    DATE,
    CHECKBOX,
    RADIO,
    SIGNATURE,
    DROPDOWN,
    MULTI_SELECT,
    STATIC_LABEL,
    UNKNOWN
}

/**
 * Bounding box for field location on PDF
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val page: Int = 1
)
