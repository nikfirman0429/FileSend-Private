package com.example.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.data.model.SelectedFileInfo
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

object FileUtils {

    fun getSelectedFileInfo(context: Context, uri: Uri): SelectedFileInfo? {
        var fileName = "unknown_file"
        var fileSize = -1L
        val contentResolver: ContentResolver = context.contentResolver

        // Query metadata from ContentResolver
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: "unknown_file"
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback for size if cursor didn't provide it
        if (fileSize <= 0) {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    fileSize = pfd.statSize
                }
            } catch (e: Exception) {
                fileSize = 0L
            }
        }

        // Get extension and MIME type
        var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val extension = getFileExtension(fileName)
        if (mimeType == "application/octet-stream" && extension.isNotEmpty()) {
            val mappedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase().removePrefix("."))
            if (mappedMime != null) {
                mimeType = mappedMime
            }
        }

        return SelectedFileInfo(
            uriString = uri.toString(),
            name = sanitizeFilename(fileName),
            size = fileSize,
            mimeType = mimeType,
            extension = extension,
            formattedSize = formatFileSize(fileSize)
        )
    }

    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot)
        } else {
            ""
        }
    }

    fun getMimeType(fileName: String): String {
        val ext = getFileExtension(fileName).lowercase().removePrefix(".")
        if (ext.isNotEmpty()) {
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!mapped.isNullOrBlank()) return mapped
        }
        return "application/octet-stream"
    }

    fun sanitizeFilename(filename: String): String {
        var safe = File(filename).name.replace("[/\\\\?%*:|\"<>]".toRegex(), "_").trim()
        if (safe.isBlank() || safe == "." || safe == "..") {
            safe = "filesend_file.bin"
        }
        return safe
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    fun formatSpeed(bytesPerSec: Double): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        return when {
            bytesPerSec >= 1024 * 1024 -> {
                val mbps = bytesPerSec / (1024 * 1024)
                String.format(Locale.US, "%.1f MB/s", mbps)
            }
            bytesPerSec >= 1024 -> {
                val kbps = bytesPerSec / 1024
                String.format(Locale.US, "%.1f KB/s", kbps)
            }
            else -> {
                String.format(Locale.US, "%.0f B/s", bytesPerSec)
            }
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "Estimating..."
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        val remainingSecs = seconds % 60
        if (minutes < 60) {
            return "${minutes}m ${remainingSecs}s"
        }
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return "${hours}h ${remMinutes}m"
    }

    fun openFile(context: Context, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val ext = file.extension.lowercase(Locale.ROOT)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = android.content.Intent.createChooser(intent, "Open with").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shareFile(context: Context, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val ext = file.extension.lowercase(Locale.ROOT)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = android.content.Intent.createChooser(shareIntent, "Share file").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
