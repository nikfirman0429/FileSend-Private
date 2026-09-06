package com.example.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object ChecksumUtil {

    /**
     * Calculates SHA-256 checksum of a file given its Uri using streaming buffers.
     * Memory usage is strictly constant (64KB buffer) regardless of file size (even 10GB+).
     */
    suspend fun calculateSha256(
        context: Context,
        uri: Uri,
        totalSize: Long,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024) // 64 KB streaming buffer
        var bytesReadTotal = 0L

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()
                digest.update(buffer, 0, bytesRead)
                bytesReadTotal += bytesRead
                if (totalSize > 0 && onProgress != null) {
                    val progress = (bytesReadTotal.toFloat() / totalSize).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
        } ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")

        val hashBytes = digest.digest()
        val hexString = StringBuilder()
        for (b in hashBytes) {
            hexString.append(String.format("%02x", b))
        }
        hexString.toString().lowercase()
    }

    /**
     * Calculates SHA-256 checksum from an InputStream.
     */
    suspend fun calculateSha256(
        inputStream: InputStream,
        totalSize: Long = -1,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesReadTotal = 0L

        inputStream.use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()
                digest.update(buffer, 0, bytesRead)
                bytesReadTotal += bytesRead
                if (totalSize > 0 && onProgress != null) {
                    val progress = (bytesReadTotal.toFloat() / totalSize).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
        }

        val hashBytes = digest.digest()
        val hexString = StringBuilder()
        for (b in hashBytes) {
            hexString.append(String.format("%02x", b))
        }
        hexString.toString().lowercase()
    }
}
