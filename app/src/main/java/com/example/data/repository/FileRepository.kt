package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.TransferHistoryDao
import com.example.data.local.TransferHistoryEntity
import com.example.data.model.ExpirationOption
import com.example.data.model.FileMetadataResponse
import com.example.data.model.HealthResponse
import com.example.data.model.TransferDirection
import com.example.data.model.TransferStatus
import com.example.data.preferences.AppPreferences
import com.example.data.remote.ApiClient
import com.example.data.remote.NetworkResult
import com.example.engine.ChunkedUploader
import com.example.engine.FileUtils
import com.example.engine.StreamingDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FileRepository(
    private val context: Context,
    private val apiClient: ApiClient,
    private val historyDao: TransferHistoryDao,
    private val appPreferences: AppPreferences
) {

    val allHistory: Flow<List<TransferHistoryEntity>> = historyDao.getAllHistory()

    suspend fun checkHealth(): NetworkResult<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.getService().checkHealth()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val msg = ApiClient.parseErrorMessage(response.errorBody()?.string(), response.code())
                NetworkResult.Error(msg, response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Cannot connect to server: ${e.localizedMessage ?: e.message}", throwable = e)
        }
    }

    suspend fun getFileInfo(code: String): NetworkResult<FileMetadataResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.getService().getFileInfo(code.trim().uppercase())
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val msg = ApiClient.parseErrorMessage(response.errorBody()?.string(), response.code())
                NetworkResult.Error(msg, response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Failed to retrieve file info"}", throwable = e)
        }
    }

    suspend fun uploadFile(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        expiration: ExpirationOption,
        onHashProgress: (Float) -> Unit,
        onProgress: (ChunkedUploader.ProgressUpdate) -> Unit
    ): NetworkResult<TransferHistoryEntity> = withContext(Dispatchers.IO) {
        var historyId = -1L
        try {
            val uploader = ChunkedUploader(context, apiClient.getService())
            val chunkSizeBytes = appPreferences.getChunkSizeBytes()

            val result = uploader.upload(
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                expiration = expiration,
                chunkSizeBytes = chunkSizeBytes,
                onHashProgress = onHashProgress,
                onProgress = onProgress
            )

            val historyEntity = TransferHistoryEntity(
                code = result.code,
                fileName = result.originalName,
                fileExtension = FileUtils.getFileExtension(result.originalName),
                mimeType = result.mimeType,
                fileSize = result.size,
                direction = TransferDirection.SEND,
                checksum = result.checksum,
                timestamp = System.currentTimeMillis(),
                status = TransferStatus.COMPLETED,
                localFilePath = uri.toString(),
                shareUrl = result.shareUrl,
                expiresAt = result.expiresAt
            )

            historyId = historyDao.insert(historyEntity)
            NetworkResult.Success(historyEntity.copy(id = historyId))
        } catch (e: Exception) {
            val historyEntity = TransferHistoryEntity(
                code = "PENDING",
                fileName = fileName,
                fileExtension = FileUtils.getFileExtension(fileName),
                mimeType = mimeType,
                fileSize = fileSize,
                direction = TransferDirection.SEND,
                checksum = "",
                timestamp = System.currentTimeMillis(),
                status = TransferStatus.FAILED,
                localFilePath = uri.toString(),
                errorMessage = e.message
            )
            historyDao.insert(historyEntity)
            NetworkResult.Error(e.message ?: "Upload failed", throwable = e)
        }
    }

    suspend fun downloadFile(
        metadata: FileMetadataResponse,
        onProgress: (StreamingDownloader.DownloadProgressUpdate) -> Unit
    ): NetworkResult<TransferHistoryEntity> = withContext(Dispatchers.IO) {
        try {
            val downloader = StreamingDownloader(context, apiClient.getService())
            val result = downloader.download(metadata, onProgress)

            val historyEntity = TransferHistoryEntity(
                code = result.code,
                fileName = result.fileName,
                fileExtension = FileUtils.getFileExtension(result.fileName),
                mimeType = metadata.mimeType,
                fileSize = result.size,
                direction = TransferDirection.RECEIVE,
                checksum = result.checksum,
                timestamp = System.currentTimeMillis(),
                status = TransferStatus.COMPLETED,
                localFilePath = result.localFilePath,
                shareUrl = metadata.shareUrl,
                expiresAt = metadata.expiresAt
            )

            val id = historyDao.insert(historyEntity)
            NetworkResult.Success(historyEntity.copy(id = id))
        } catch (e: Exception) {
            val historyEntity = TransferHistoryEntity(
                code = metadata.code,
                fileName = metadata.originalName,
                fileExtension = FileUtils.getFileExtension(metadata.originalName),
                mimeType = metadata.mimeType,
                fileSize = metadata.size,
                direction = TransferDirection.RECEIVE,
                checksum = metadata.checksum,
                timestamp = System.currentTimeMillis(),
                status = TransferStatus.FAILED,
                errorMessage = e.message
            )
            historyDao.insert(historyEntity)
            NetworkResult.Error(e.message ?: "Download failed", throwable = e)
        }
    }

    suspend fun deleteHistory(history: TransferHistoryEntity) = withContext(Dispatchers.IO) {
        historyDao.delete(history)
    }

    suspend fun deleteHistoryById(id: Long) = withContext(Dispatchers.IO) {
        historyDao.deleteById(id)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        historyDao.clearAll()
    }
}
