package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.model.FileMetadataResponse
import com.example.data.remote.ApiClient
import com.example.data.remote.FileSendApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class StreamingDownloader(
    private val context: Context,
    private val apiService: FileSendApiService
) {

    data class DownloadProgressUpdate(
        val code: String,
        val fileName: String,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Double,
        val etaSeconds: Long
    )

    data class DownloadResult(
        val code: String,
        val fileName: String,
        val size: Long,
        val checksum: String,
        val localFilePath: String,
        val isChecksumVerified: Boolean
    )

    suspend fun download(
        fileMetadata: FileMetadataResponse,
        onProgress: (DownloadProgressUpdate) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val code = fileMetadata.code
        val fileName = FileUtils.sanitizeFilename(fileMetadata.originalName)
        val expectedSize = fileMetadata.size
        val expectedChecksum = fileMetadata.checksum.lowercase()

        // Prepare local destination file
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        downloadsDir.mkdirs()

        // If duplicate filename exists, generate unique name
        var targetFile = File(downloadsDir, fileName)
        if (targetFile.exists()) {
            val nameWithoutExt = targetFile.nameWithoutExtension
            val ext = if (targetFile.extension.isNotEmpty()) ".${targetFile.extension}" else ""
            targetFile = File(downloadsDir, "${nameWithoutExt}_${System.currentTimeMillis()}$ext")
        }

        val tempFile = File(downloadsDir, "${targetFile.name}.downloading")
        if (tempFile.exists()) tempFile.delete()

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val response = apiService.downloadFileStream(code)
            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = ApiClient.parseErrorMessage(
                    response.errorBody()?.string(),
                    response.code()
                )
                throw Exception("Download request failed: $errorMsg")
            }

            val body = response.body()!!
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else expectedSize

            inputStream = body.byteStream()
            outputStream = FileOutputStream(tempFile)

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024) // 64 KB streaming buffer
            var bytesReadTotal = 0L

            val startTime = System.currentTimeMillis()
            var lastSpeedCalcTime = startTime
            var lastDownloadedBytes = 0L
            var currentSpeed = 0.0

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()

                outputStream.write(buffer, 0, bytesRead)
                digest.update(buffer, 0, bytesRead)
                bytesReadTotal += bytesRead

                val now = System.currentTimeMillis()
                val timeDiff = (now - lastSpeedCalcTime) / 1000.0
                if (timeDiff >= 0.5) {
                    val bytesDiff = bytesReadTotal - lastDownloadedBytes
                    currentSpeed = if (timeDiff > 0) bytesDiff / timeDiff else currentSpeed
                    lastSpeedCalcTime = now
                    lastDownloadedBytes = bytesReadTotal
                }

                val remainingBytes = (totalBytes - bytesReadTotal).coerceAtLeast(0)
                val eta = if (currentSpeed > 0) (remainingBytes / currentSpeed).toLong() else 0L
                val percent = if (totalBytes > 0) ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

                onProgress(
                    DownloadProgressUpdate(
                        code = code,
                        fileName = fileName,
                        progressPercent = percent,
                        downloadedBytes = bytesReadTotal,
                        totalBytes = totalBytes,
                        speedBytesPerSec = currentSpeed,
                        etaSeconds = eta
                    )
                )
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null
            inputStream.close()
            inputStream = null

            // Verify SHA-256 Checksum
            val hashBytes = digest.digest()
            val hexString = StringBuilder()
            for (b in hashBytes) {
                hexString.append(String.format("%02x", b))
            }
            val calculatedChecksum = hexString.toString().lowercase()

            if (expectedChecksum.isNotBlank() && calculatedChecksum != expectedChecksum) {
                if (tempFile.exists()) tempFile.delete()
                throw SecurityException("File integrity verification failed: Checksum mismatch (Expected: $expectedChecksum, Actual: $calculatedChecksum). The downloaded file may be corrupted.")
            }

            // Rename .downloading temp file to final destination
            if (targetFile.exists()) targetFile.delete()
            val renameSuccess = tempFile.renameTo(targetFile)
            val finalPath = if (renameSuccess) targetFile.absolutePath else tempFile.absolutePath

            return@withContext DownloadResult(
                code = code,
                fileName = fileName,
                size = bytesReadTotal,
                checksum = calculatedChecksum,
                localFilePath = finalPath,
                isChecksumVerified = true
            )

        } catch (e: CancellationException) {
            // Clean up temp file on cancel
            try {
                outputStream?.close()
                inputStream?.close()
                if (tempFile.exists()) tempFile.delete()
            } catch (ex: Exception) {}
            throw e
        } catch (e: Exception) {
            try {
                outputStream?.close()
                inputStream?.close()
                if (tempFile.exists()) tempFile.delete()
            } catch (ex: Exception) {}
            throw e
        }
    }
}
