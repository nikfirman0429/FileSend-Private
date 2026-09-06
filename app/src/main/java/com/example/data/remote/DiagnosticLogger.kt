package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogCategory {
    HTTP_REQUEST,
    HTTP_RESPONSE,
    HTTP_ERROR,
    SEARCH_OPERATION,
    UPLOAD_OPERATION,
    CLOUD_RELAY,
    SYSTEM
}

data class DiagnosticLogEntry(
    val id: Long,
    val timestamp: Long,
    val category: LogCategory,
    val tag: String,
    val method: String? = null,
    val url: String? = null,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val requestHeaders: Map<String, String>? = null,
    val requestBody: String? = null,
    val responseHeaders: Map<String, String>? = null,
    val responseBody: String? = null,
    val isError: Boolean = false,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    val statusBadge: String
        get() = when {
            statusCode != null && statusCode in 200..299 -> "HTTP $statusCode OK"
            statusCode != null && statusCode == 404 -> "HTTP 404 NOT FOUND"
            statusCode != null && statusCode in 400..499 -> "HTTP $statusCode CLIENT ERROR"
            statusCode != null && statusCode in 500..599 -> "HTTP $statusCode SERVER ERROR"
            statusCode != null -> "HTTP $statusCode"
            isError -> "FAILED"
            else -> "INFO"
        }
}

/**
 * Real-time diagnostic logger for all Retrofit/OkHttp network requests,
 * backend responses, 6-digit code discovery, and cloud relay operations.
 */
object DiagnosticLogger {

    private const val TAG = "FileSendNetDiag"
    private const val MAX_LOG_ENTRIES = 200

    private val idCounter = AtomicLong(1)
    private val logList = mutableListOf<DiagnosticLogEntry>()

    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    private val _lastOperationSummary = MutableStateFlow<String?>(null)
    val lastOperationSummary: StateFlow<String?> = _lastOperationSummary.asStateFlow()

    init {
        logInfo(
            tag = "INIT",
            message = "Retrofit Diagnostic Logger initialized. Capturing all network transactions.",
            category = LogCategory.SYSTEM
        )
    }

    @Synchronized
    fun logHttpTransaction(
        method: String,
        url: String,
        statusCode: Int?,
        durationMs: Long,
        requestHeaders: Map<String, String>? = null,
        requestBody: String? = null,
        responseHeaders: Map<String, String>? = null,
        responseBody: String? = null,
        isError: Boolean = (statusCode == null || statusCode >= 400),
        errorMessage: String? = null,
        tag: String = "RETROFIT_HTTP"
    ) {
        val entry = DiagnosticLogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            category = if (isError) LogCategory.HTTP_ERROR else LogCategory.HTTP_RESPONSE,
            tag = tag,
            method = method,
            url = url,
            statusCode = statusCode,
            durationMs = durationMs,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            isError = isError,
            message = errorMessage ?: "[$method] $url -> ${statusCode ?: "ERROR"} (${durationMs}ms)",
            details = buildString {
                appendLine("=== HTTP TRANSACTION DIAGNOSTIC ===")
                appendLine("Method: $method")
                appendLine("URL: $url")
                appendLine("Status Code: ${statusCode ?: "NONE (Connection Failed)"}")
                appendLine("Duration: ${durationMs}ms")
                if (!requestBody.isNullOrBlank()) {
                    appendLine("\n--- Request Body ---")
                    appendLine(requestBody.take(2000))
                }
                if (!responseBody.isNullOrBlank()) {
                    appendLine("\n--- Response Body ---")
                    appendLine(responseBody.take(4000))
                }
                if (!errorMessage.isNullOrBlank()) {
                    appendLine("\n--- Error Note ---")
                    appendLine(errorMessage)
                }
            }
        )

        addEntry(entry)

        // Log to Android Logcat
        val logMsg = "[$method] $url -> Code: $statusCode | Duration: ${durationMs}ms | Resp: ${responseBody?.take(300)}"
        if (isError) {
            try { Log.e(TAG, logMsg) } catch (e: Throwable) { System.err.println("[$TAG] ERROR: $logMsg") }
        } else {
            try { Log.d(TAG, logMsg) } catch (e: Throwable) { println("[$TAG] $logMsg") }
        }

        _lastOperationSummary.value = "Last: $method $url -> ${statusCode ?: "ERR"}"
    }

    @Synchronized
    fun logInfo(
        tag: String,
        message: String,
        details: String? = null,
        category: LogCategory = LogCategory.SYSTEM
    ) {
        val entry = DiagnosticLogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            category = category,
            tag = tag,
            isError = false,
            message = message,
            details = details
        )
        addEntry(entry)
        try { Log.i(TAG, "[$tag] $message") } catch (e: Throwable) { println("[$TAG] [$tag] $message") }
    }

    @Synchronized
    fun logError(
        tag: String,
        message: String,
        details: String? = null,
        category: LogCategory = LogCategory.HTTP_ERROR
    ) {
        val entry = DiagnosticLogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            category = category,
            tag = tag,
            isError = true,
            message = message,
            details = details
        )
        addEntry(entry)
        try { Log.e(TAG, "[$tag] ERROR: $message") } catch (e: Throwable) { System.err.println("[$TAG] ERROR: [$tag] $message") }
    }

    private fun addEntry(entry: DiagnosticLogEntry) {
        logList.add(0, entry)
        if (logList.size > MAX_LOG_ENTRIES) {
            logList.removeAt(logList.lastIndex)
        }
        _logs.value = logList.toList()
    }

    @Synchronized
    fun clearLogs() {
        logList.clear()
        _logs.value = emptyList()
        _lastOperationSummary.value = null
        logInfo("SYSTEM", "Diagnostic logs cleared.")
    }

    fun exportDiagnosticsText(): String {
        val currentLogs = _logs.value
        return buildString {
            appendLine("==================================================")
            appendLine("FILESEND RETROFIT NETWORK DIAGNOSTIC LOG")
            appendLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Total Entries: ${currentLogs.size}")
            appendLine("==================================================\n")

            if (currentLogs.isEmpty()) {
                appendLine("No network transactions recorded.")
            } else {
                currentLogs.forEach { log ->
                    appendLine("[${log.formattedTime}] [${log.tag}] ${log.statusBadge}")
                    if (log.url != null) {
                        appendLine("URL: ${log.method ?: "GET"} ${log.url}")
                    }
                    if (log.statusCode != null) {
                        appendLine("Status Code: ${log.statusCode} (Duration: ${log.durationMs ?: 0}ms)")
                    }
                    appendLine("Message: ${log.message}")
                    if (!log.responseBody.isNullOrBlank()) {
                        appendLine("Response Body:\n${log.responseBody}")
                    }
                    if (!log.details.isNullOrBlank() && log.details != log.responseBody) {
                        appendLine("Details:\n${log.details}")
                    }
                    appendLine("--------------------------------------------------")
                }
            }
        }
    }
}
