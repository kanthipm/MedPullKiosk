package com.medpull.kiosk.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.UploadStatus

@Entity(
    tableName = "documents",
    indices = [Index("sessionId")]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val documentType: String,       // DocumentType.name
    val filePath: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val uploadStatus: String        // UploadStatus.name
) {
    fun documentType(): DocumentType = DocumentType.valueOf(documentType)
    fun uploadStatus(): UploadStatus = UploadStatus.valueOf(uploadStatus)
}
