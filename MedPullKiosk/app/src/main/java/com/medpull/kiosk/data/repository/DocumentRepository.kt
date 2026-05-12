package com.medpull.kiosk.data.repository

import android.content.Context
import android.net.Uri
import com.medpull.kiosk.data.local.dao.DocumentDao
import com.medpull.kiosk.data.local.entities.DocumentEntity
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.UploadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    @ApplicationContext private val context: Context
) {

    fun getDocumentsFlow(sessionId: String): Flow<List<DocumentEntity>> =
        documentDao.getBySessionId(sessionId)

    suspend fun getDocumentsOnce(sessionId: String): List<DocumentEntity> =
        documentDao.getBySessionIdOnce(sessionId)

    /**
     * Copy a content URI (from file picker or camera) to internal storage
     * and persist the document record.
     */
    suspend fun saveDocument(
        sessionId: String,
        type: DocumentType,
        sourceUri: Uri
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val destFile = destFile(sessionId, type, extensionFromUri(sourceUri))
        destFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        val entity = DocumentEntity(
            id = "${sessionId}_${type.name}",
            sessionId = sessionId,
            documentType = type.name,
            filePath = destFile.absolutePath,
            timestamp = System.currentTimeMillis(),
            uploadStatus = UploadStatus.UPLOADED.name
        )
        documentDao.upsert(entity)
        entity
    }

    /**
     * Create an empty temp file for the camera to write into, then call
     * [confirmCameraCapture] after the capture succeeds.
     */
    suspend fun createTempCameraFile(sessionId: String, type: DocumentType): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "documents/$sessionId")
            dir.mkdirs()
            File(dir, "TEMP_${type.name}.jpg")
        }

    /**
     * Called after [ActivityResultContracts.TakePicture] returns true.
     * The file already has data written by the camera app.
     */
    suspend fun confirmCameraCapture(
        sessionId: String,
        type: DocumentType,
        tempFile: File
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val dest = destFile(sessionId, type, "jpg")
        if (tempFile.absolutePath != dest.absolutePath) {
            tempFile.copyTo(dest, overwrite = true)
            tempFile.delete()
        }
        val entity = DocumentEntity(
            id = "${sessionId}_${type.name}",
            sessionId = sessionId,
            documentType = type.name,
            filePath = dest.absolutePath,
            timestamp = System.currentTimeMillis(),
            uploadStatus = UploadStatus.UPLOADED.name
        )
        documentDao.upsert(entity)
        entity
    }

    /**
     * Mark a document type as skipped for this session.
     */
    suspend fun skipDocument(sessionId: String, type: DocumentType) {
        val entity = DocumentEntity(
            id = "${sessionId}_${type.name}",
            sessionId = sessionId,
            documentType = type.name,
            filePath = null,
            timestamp = System.currentTimeMillis(),
            uploadStatus = UploadStatus.SKIPPED.name
        )
        documentDao.upsert(entity)
    }

    /**
     * Delete the stored file and remove the document record.
     */
    suspend fun deleteDocument(sessionId: String, type: DocumentType) =
        withContext(Dispatchers.IO) {
            val existing = documentDao.getBySessionAndType(sessionId, type.name)
            existing?.filePath?.let { File(it).delete() }
            documentDao.deleteBySessionAndType(sessionId, type.name)
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun destFile(sessionId: String, type: DocumentType, ext: String): File {
        val dir = File(context.filesDir, "documents/$sessionId")
        dir.mkdirs()
        return File(dir, "${type.name}.$ext")
    }

    private fun extensionFromUri(uri: Uri): String {
        val path = uri.path ?: return "jpg"
        return when {
            path.endsWith(".pdf", ignoreCase = true) -> "pdf"
            path.endsWith(".png", ignoreCase = true) -> "png"
            else -> "jpg"
        }
    }
}
