package com.medpull.kiosk.data.repository

import android.util.Log
import com.medpull.kiosk.data.local.entities.SyncOperationType
import com.medpull.kiosk.data.remote.aws.S3Service
import com.medpull.kiosk.data.remote.aws.UploadProgress
import com.medpull.kiosk.data.remote.aws.UploadResult
import com.medpull.kiosk.data.remote.aws.DownloadResult
import com.medpull.kiosk.sync.SyncManager
import com.medpull.kiosk.sync.UploadFilePayload
import com.medpull.kiosk.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for file storage operations
 * Handles S3 uploads/downloads with offline queue
 */
@Singleton
class StorageRepository @Inject constructor(
    private val s3Service: S3Service,
    private val networkMonitor: NetworkMonitor,
    private val syncManager: SyncManager
) {

    companion object {
        private const val TAG = "StorageRepository"
    }

    /**
     * Upload form PDF to S3
     * If offline, queues for later sync
     */
    suspend fun uploadForm(
        file: File,
        userId: String
    ): UploadResult {
        if (!networkMonitor.isCurrentlyConnected()) {
            Log.d(TAG, "Offline: queuing form upload for ${file.name}")

            // Queue for later sync
            val s3Key = "forms/$userId/${file.name}"
            syncManager.queueOperation(
                operationType = SyncOperationType.UPLOAD_FILE,
                entityId = s3Key,
                payload = UploadFilePayload(
                    localFilePath = file.absolutePath,
                    s3Key = s3Key
                ),
                priority = 1
            )

            return UploadResult.QueuedForSync(
                message = "Queued for upload when online",
                localPath = file.absolutePath
            )
        }

        return s3Service.uploadFileSync(
            file = file,
            folder = "forms/",
            userId = userId
        )
    }

    /**
     * Upload form with progress tracking
     */
    fun uploadFormWithProgress(
        file: File,
        userId: String
    ): Flow<UploadProgress> {
        return s3Service.uploadFile(
            file = file,
            folder = "forms/",
            userId = userId
        )
    }

    /**
     * Download form PDF from S3
     */
    suspend fun downloadForm(
        s3Key: String,
        destinationFile: File
    ): DownloadResult {
        return s3Service.downloadFile(s3Key, destinationFile)
    }

    /**
     * Upload filled form to S3
     * If offline, queues for later sync
     * Returns Result for easier ViewModel handling
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun uploadFilledForm(
        file: File,
        userId: String,
        formId: String
    ): Result<String> {
        return try {
            if (!networkMonitor.isCurrentlyConnected()) {
                Log.d(TAG, "Offline: queuing filled form upload for ${file.name}")

                // Queue for later sync
                val s3Key = "filled-forms/$userId/${file.name}"
                syncManager.queueOperation(
                    operationType = SyncOperationType.UPLOAD_FILE,
                    entityId = s3Key,
                    payload = UploadFilePayload(
                        localFilePath = file.absolutePath,
                        s3Key = s3Key
                    ),
                    priority = 2  // Higher priority for filled forms
                )

                Result.success("Queued for upload when online")
            } else {
                val uploadResult = s3Service.uploadFileSync(
                    file = file,
                    folder = "filled-forms/",
                    userId = userId
                )

                when (uploadResult) {
                    is UploadResult.Success -> Result.success(uploadResult.s3Key)
                    is UploadResult.Error -> Result.failure(Exception(uploadResult.message))
                    is UploadResult.QueuedForSync -> Result.success(uploadResult.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading filled form", e)
            Result.failure(e)
        }
    }

    /**
     * Upload audit logs to S3
     */
    suspend fun uploadAuditLog(
        logData: String,
        userId: String,
        timestamp: Long
    ): Boolean {
        return s3Service.uploadAuditLog(logData, userId, timestamp)
    }

    /**
     * Get presigned URL for file access
     */
    suspend fun getFileUrl(s3Key: String): String? {
        return s3Service.getFileUrl(s3Key)
    }

    /**
     * Check if file exists in S3
     */
    suspend fun fileExists(s3Key: String): Boolean {
        return s3Service.fileExists(s3Key)
    }

    /**
     * Delete file from S3
     */
    suspend fun deleteFile(s3Key: String): Boolean {
        return s3Service.deleteFile(s3Key)
    }

    /**
     * List user's forms
     */
    suspend fun listUserForms(userId: String) = s3Service.listFiles("forms/", userId)

    /**
     * List user's filled forms
     */
    suspend fun listUserFilledForms(userId: String) = s3Service.listFiles("filled-forms/", userId)
}
