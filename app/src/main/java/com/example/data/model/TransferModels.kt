package com.example.data.model

enum class TransferDirection {
    SEND,
    RECEIVE
}

enum class TransferStatus {
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED,
    IN_PROGRESS
}

enum class ExpirationOption(val hours: Int, val label: String) {
    ONE_HOUR(1, "1 Hour"),
    SIX_HOURS(6, "6 Hours"),
    TWENTY_FOUR_HOURS(24, "24 Hours (Default)"),
    SEVEN_DAYS(168, "7 Days"),
    NEVER(0, "Never")
}

data class SelectedFileInfo(
    val uriString: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val extension: String,
    val formattedSize: String
)

sealed class TransferProgressState {
    object Idle : TransferProgressState()
    
    data class CalculatingHash(
        val fileName: String,
        val progress: Float
    ) : TransferProgressState()
    
    data class Uploading(
        val fileName: String,
        val progressPercent: Int,
        val uploadedBytes: Long,
        val totalBytes: Long,
        val currentChunk: Int,
        val totalChunks: Int,
        val speedBytesPerSec: Double,
        val etaSeconds: Long
    ) : TransferProgressState()
    
    data class UploadSuccess(
        val code: String,
        val fileName: String,
        val size: Long,
        val checksum: String,
        val shareUrl: String,
        val expiresAt: Long?
    ) : TransferProgressState()
    
    data class Downloading(
        val code: String,
        val fileName: String,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Double,
        val etaSeconds: Long
    ) : TransferProgressState()
    
    data class DownloadSuccess(
        val code: String,
        val fileName: String,
        val size: Long,
        val checksum: String,
        val localFilePath: String,
        val isChecksumVerified: Boolean
    ) : TransferProgressState()
    
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val errorCode: Int? = null
    ) : TransferProgressState()
    
    data class Cancelled(
        val message: String = "Transfer was cancelled."
    ) : TransferProgressState()
}
