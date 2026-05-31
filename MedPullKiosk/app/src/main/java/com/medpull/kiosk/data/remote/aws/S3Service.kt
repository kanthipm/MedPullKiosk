package com.medpull.kiosk.data.remote.aws

import android.util.Log
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.medpull.kiosk.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AWS S3 service for file upload and download
 * Supports progress tracking and offline queue
 */
@Singleton
class S3Service @Inject constructor(
    private val s3Client: AmazonS3Client
) {

    companion object {
        private const val TAG = "S3Service"
    }

    private val bucketName = Constants.AWS.S3_BUCKET

    /**
     * Handle AWS service exceptions with specific error messages
     */
    private fun handleAwsException(e: Exception, operation: String): String {
        return when (e) {
            is AmazonServiceException -> {
                val msg = e.errorMessage ?: ""
                when {
                    msg.contains("login token", ignoreCase = true) ||
                    msg.contains("Token expired", ignoreCase = true) -> {
                        Log.e(TAG, "$operation: Invalid login token - credential refresh needed", e)
                        "Session credentials expired. Please sign out and sign back in."
                    }
                    e.statusCode == 401 || e.statusCode == 403 -> {
                        Log.e(TAG, "$operation: Access denied - credentials may be invalid or expired", e)
                        "Access denied. Please try logging in again."
                    }
                    e.statusCode == 404 -> {
                        Log.e(TAG, "$operation: Resource not found", e)
                        "File not found."
                    }
                    e.statusCode == 429 || e.statusCode == 503 -> {
                        Log.e(TAG, "$operation: Service throttled or unavailable", e)
                        "Service temporarily unavailable. Please try again."
                    }
                    e.statusCode in listOf(500, 502, 504) -> {
                        Log.e(TAG, "$operation: Server error", e)
                        "Server error. Please try again later."
                    }
                    else -> {
                        Log.e(TAG, "$operation: AWS error (${e.statusCode}): ${e.errorMessage}", e)
                        "Upload failed: ${e.errorMessage}"
                    }
                }
            }
            else -> {
                Log.e(TAG, "$operation: Unexpected error", e)
                e.message ?: "Operation failed"
            }
        }
    }

    /**
     * Upload file to S3 with progress tracking
     */
    fun uploadFile(
        file: File,
        folder: String = Constants.AWS.S3_FORMS_FOLDER,
        userId: String
    ): Flow<UploadProgress> = flow {
        emit(UploadProgress.Started)

        try {
            val fileName = "${UUID.randomUUID()}_${file.name}"
            val s3Key = "$folder$userId/$fileName"

            // Create metadata
            val metadata = ObjectMetadata().apply {
                contentLength = file.length()
                contentType = "application/pdf"
                addUserMetadata("uploaded-by", userId)
                addUserMetadata("upload-timestamp", System.currentTimeMillis().toString())
            }

            // Create put request using File directly (AWS SDK handles retries automatically)
            val putRequest = PutObjectRequest(bucketName, s3Key, file).apply {
                setMetadata(metadata)
            }

            // Perform upload
            withContext(Dispatchers.IO) {
                s3Client.putObject(putRequest)
            }

            emit(UploadProgress.Success(s3Key, fileName))
        } catch (e: Exception) {
            val errorMessage = handleAwsException(e, "Upload file")
            emit(UploadProgress.Error(errorMessage))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Upload file synchronously (for use in repositories)
     */
    suspend fun uploadFileSync(
        file: File,
        folder: String = Constants.AWS.S3_FORMS_FOLDER,
        userId: String
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val fileName = "${UUID.randomUUID()}_${file.name}"
            val s3Key = "$folder$userId/$fileName"

            val metadata = ObjectMetadata().apply {
                contentLength = file.length()
                contentType = "application/pdf"
                addUserMetadata("uploaded-by", userId)
                addUserMetadata("upload-timestamp", System.currentTimeMillis().toString())
            }

            // Use File directly (AWS SDK handles retries by reopening the file)
            val putRequest = PutObjectRequest(bucketName, s3Key, file).apply {
                setMetadata(metadata)
            }

            s3Client.putObject(putRequest)

            UploadResult.Success(s3Key, fileName)
        } catch (e: Exception) {
            val errorMessage = handleAwsException(e, "Upload file sync")
            UploadResult.Error(errorMessage)
        }
    }

    /**
     * Download file from S3
     */
    suspend fun downloadFile(
        s3Key: String,
        destinationFile: File
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val getRequest = GetObjectRequest(bucketName, s3Key)
            val s3Object: S3Object = s3Client.getObject(getRequest)

            // Write to file
            s3Object.objectContent.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            DownloadResult.Success(destinationFile.absolutePath)
        } catch (e: Exception) {
            val errorMessage = handleAwsException(e, "Download file")
            DownloadResult.Error(errorMessage)
        }
    }

    /**
     * Download file with progress tracking
     */
    fun downloadFileWithProgress(
        s3Key: String,
        destinationFile: File
    ): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Started)

        try {
            val getRequest = GetObjectRequest(bucketName, s3Key)
            val s3Object: S3Object = withContext(Dispatchers.IO) {
                s3Client.getObject(getRequest)
            }

            val totalBytes = s3Object.objectMetadata.contentLength
            var bytesRead = 0L

            withContext(Dispatchers.IO) {
                s3Object.objectContent.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
                            emit(DownloadProgress.Progress(progress, bytesRead, totalBytes))
                        }
                    }
                }
            }

            emit(DownloadProgress.Success(destinationFile.absolutePath))
        } catch (e: Exception) {
            val errorMessage = handleAwsException(e, "Download file with progress")
            emit(DownloadProgress.Error(errorMessage))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Delete file from S3
     */
    suspend fun deleteFile(s3Key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            s3Client.deleteObject(bucketName, s3Key)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if file exists in S3
     */
    suspend fun fileExists(s3Key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            s3Client.getObjectMetadata(bucketName, s3Key)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get file URL (presigned URL for temporary access)
     */
    suspend fun getFileUrl(s3Key: String, expirationMinutes: Int = 60): String? = withContext(Dispatchers.IO) {
        try {
            val expiration = java.util.Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000)
            s3Client.generatePresignedUrl(bucketName, s3Key, expiration).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * List files in folder
     */
    suspend fun listFiles(folder: String, userId: String): List<S3FileInfo> = withContext(Dispatchers.IO) {
        try {
            val prefix = "$folder$userId/"
            val listing = s3Client.listObjects(bucketName, prefix)

            listing.objectSummaries.map { summary ->
                S3FileInfo(
                    key = summary.key,
                    size = summary.size,
                    lastModified = summary.lastModified.time,
                    fileName = summary.key.substringAfterLast("/")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Upload audit logs
     */
    suspend fun uploadAuditLog(
        logData: String,
        userId: String,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val s3Key = "${Constants.AWS.S3_AUDIT_LOGS_FOLDER}$userId/audit_$timestamp.json"

            val metadata = ObjectMetadata().apply {
                contentLength = logData.toByteArray().size.toLong()
                contentType = "application/json"
            }

            val putRequest = PutObjectRequest(
                bucketName,
                s3Key,
                logData.byteInputStream(),
                metadata
            )

            s3Client.putObject(putRequest)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Upload progress sealed class
 */
sealed class UploadProgress {
    object Started : UploadProgress()
    data class Progress(val percent: Int, val bytesTransferred: Long, val totalBytes: Long) : UploadProgress()
    data class Success(val s3Key: String, val fileName: String) : UploadProgress()
    data class Error(val message: String) : UploadProgress()
}

/**
 * Upload result sealed class
 */
sealed class UploadResult {
    data class Success(val s3Key: String, val fileName: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
    data class QueuedForSync(val message: String, val localPath: String) : UploadResult()
}

/**
 * Download progress sealed class
 */
sealed class DownloadProgress {
    object Started : DownloadProgress()
    data class Progress(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
    data class Success(val filePath: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

/**
 * Download result sealed class
 */
sealed class DownloadResult {
    data class Success(val filePath: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

/**
 * S3 file info data class
 */
data class S3FileInfo(
    val key: String,
    val size: Long,
    val lastModified: Long,
    val fileName: String
)
