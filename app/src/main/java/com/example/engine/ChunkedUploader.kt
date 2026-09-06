package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.model.ExpirationOption
import com.example.data.model.UploadCompleteRequest
import com.example.data.model.UploadCompleteResponse
import com.example.data.model.UploadInitRequest
import com.example.data.remote.ApiClient
import com.example.data.remote.FileSendApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import kotlin.coroutines.coroutineContext

class ChunkedUploader(
    private val context: Context,
    private val apiService: FileSendApiService
) {

    data class ProgressUpdate(
        val fileName: String,
        val progressPercent: Int,
        val uploadedBytes: Long,
        val totalBytes: Long,
        val currentChunk: Int,
        val totalChunks: Int,
        val speedBytesPerSec: Double,
        val etaSeconds: Long
    )

    suspend fun upload(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        expiration: ExpirationOption,
        chunkSizeBytes: Long,
        onHashProgress: (Float) -> Unit,
        onProgress: (ProgressUpdate) -> Unit
    ): UploadCompleteResponse = withContext(Dispatchers.IO) {
        // Step 1: Calculate SHA-256 Checksum using streaming buffer
        val checksum = ChecksumUtil.calculateSha256(context, uri, fileSize) { progress ->
            onHashProgress(progress)
        }
        coroutineContext.ensureActive()

        // Step 2: Calculate total chunks count
        val chunkSize = chunkSizeBytes.coerceAtLeast(1024 * 1024L) // Min 1MB
        val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

        // Step 3: Initialize upload session with server
        val initRequest = UploadInitRequest(
            originalName = fileName,
            size = fileSize,
            totalChunks = totalChunks,
            checksum = checksum,
            mimeType = mimeType,
            expirationHours = expiration.hours
        )

        val initResponse = apiService.initUpload(initRequest)
        if (!initResponse.isSuccessful || initResponse.body() == null) {
            val errorMsg = ApiClient.parseErrorMessage(
                initResponse.errorBody()?.string(),
                initResponse.code()
            )
            throw Exception("Upload initialization failed: $errorMsg")
        }

        val session = initResponse.body()!!
        val uploadToken = session.uploadToken

        // Step 4: Stream and Upload each chunk sequentially
        var uploadedBytes = 0L
        val startTime = System.currentTimeMillis()
        var lastSpeedCalcTime = startTime
        var lastUploadedBytes = 0L
        var currentSpeed = 0.0

        for (chunkIndex in 0 until totalChunks) {
            coroutineContext.ensureActive()

            val offset = chunkIndex * chunkSize
            val bytesInThisChunk = if (chunkIndex == totalChunks - 1) {
                fileSize - offset
            } else {
                chunkSize
            }.toInt()

            val chunkData = readChunk(uri, offset, bytesInThisChunk)
            coroutineContext.ensureActive()

            // Upload chunk with exponential backoff retry
            var attempts = 0
            var uploadSuccess = false
            var lastError: Exception? = null

            while (attempts < 3 && !uploadSuccess) {
                coroutineContext.ensureActive()
                attempts++
                try {
                    val tokenPart = uploadToken.toRequestBody("text/plain".toMediaTypeOrNull())
                    val indexPart = chunkIndex.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData(
                        "chunk",
                        "chunk_$chunkIndex.tmp",
                        chunkData.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                    )

                    val response = apiService.uploadChunk(
                        headerToken = uploadToken,
                        headerIndex = chunkIndex,
                        uploadToken = tokenPart,
                        chunkIndex = indexPart,
                        chunk = filePart
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        uploadSuccess = true
                    } else {
                        val errMsg = ApiClient.parseErrorMessage(response.errorBody()?.string(), response.code())
                        throw Exception(errMsg)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (attempts < 3) {
                        delay(500L * (1L shl (attempts - 1))) // 500ms, 1000ms
                    }
                }
            }

            if (!uploadSuccess) {
                throw Exception("Failed to upload chunk ${chunkIndex + 1}/$totalChunks after 3 attempts: ${lastError?.message}")
            }

            uploadedBytes += bytesInThisChunk

            // Calculate live speed and ETA
            val now = System.currentTimeMillis()
            val timeDiff = (now - lastSpeedCalcTime) / 1000.0
            if (timeDiff >= 0.5 || chunkIndex == totalChunks - 1) {
                val bytesDiff = uploadedBytes - lastUploadedBytes
                currentSpeed = if (timeDiff > 0) bytesDiff / timeDiff else currentSpeed
                lastSpeedCalcTime = now
                lastUploadedBytes = uploadedBytes
            }

            val remainingBytes = (fileSize - uploadedBytes).coerceAtLeast(0)
            val eta = if (currentSpeed > 0) (remainingBytes / currentSpeed).toLong() else 0L
            val percent = if (fileSize > 0) ((uploadedBytes * 100) / fileSize).toInt().coerceIn(0, 100) else 100

            onProgress(
                ProgressUpdate(
                    fileName = fileName,
                    progressPercent = percent,
                    uploadedBytes = uploadedBytes,
                    totalBytes = fileSize,
                    currentChunk = chunkIndex + 1,
                    totalChunks = totalChunks,
                    speedBytesPerSec = currentSpeed,
                    etaSeconds = eta
                )
            )
        }

        // Step 5: Complete upload and trigger server verification
        val completeResponse = apiService.completeUpload(UploadCompleteRequest(uploadToken))
        if (!completeResponse.isSuccessful || completeResponse.body() == null) {
            val errorMsg = ApiClient.parseErrorMessage(
                completeResponse.errorBody()?.string(),
                completeResponse.code()
            )
            throw Exception("Failed to finalize upload on server: $errorMsg")
        }

        return@withContext completeResponse.body()!!
    }

    private fun readChunk(uri: Uri, offset: Long, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

            // Skip to offset
            var skipped = 0L
            while (skipped < offset) {
                val s = inputStream.skip(offset - skipped)
                if (s <= 0) {
                    // Fallback read
                    val b = inputStream.read()
                    if (b == -1) break
                    skipped++
                } else {
                    skipped += s
                }
            }

            // Read exactly length bytes
            var totalRead = 0
            while (totalRead < length) {
                val count = inputStream.read(buffer, totalRead, length - totalRead)
                if (count == -1) break
                totalRead += count
            }

            return if (totalRead == length) {
                buffer
            } else {
                buffer.copyOf(totalRead)
            }
        } finally {
            inputStream?.close()
        }
    }
}
