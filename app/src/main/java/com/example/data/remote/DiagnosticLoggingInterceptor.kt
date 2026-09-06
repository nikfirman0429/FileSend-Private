package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * An OkHttp/Retrofit Interceptor that logs comprehensive network diagnostics,
 * capturing exact HTTP method, target URL, response status code, execution duration,
 * headers, and peeked response payload without consuming the network stream.
 */
class DiagnosticLoggingInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val urlString = request.url.toString()
        val method = request.method

        // Extract request payload summary if present (skip massive multipart uploads to conserve RAM)
        val requestBodySummary = extractRequestBody(request)

        val requestHeaders = mutableMapOf<String, String>()
        for (name in request.headers.names()) {
            requestHeaders[name] = request.headers[name] ?: ""
        }

        try {
            val response = chain.proceed(request)
            val durationMs = System.currentTimeMillis() - startTime
            val statusCode = response.code

            // Peek up to 64KB of response body safely without exhausting stream
            val responseBodyString = try {
                response.peekBody(64 * 1024L).string()
            } catch (e: Exception) {
                "[Response body could not be previewed: ${e.message}]"
            }

            val responseHeaders = mutableMapOf<String, String>()
            for (name in response.headers.names()) {
                responseHeaders[name] = response.headers[name] ?: ""
            }

            val isError = !response.isSuccessful

            // Record to in-app diagnostic log manager
            DiagnosticLogger.logHttpTransaction(
                method = method,
                url = urlString,
                statusCode = statusCode,
                durationMs = durationMs,
                requestHeaders = requestHeaders,
                requestBody = requestBodySummary,
                responseHeaders = responseHeaders,
                responseBody = responseBodyString,
                isError = isError,
                errorMessage = if (isError) "HTTP $statusCode (${response.message})" else null,
                tag = determineTag(urlString)
            )

            return response
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            val errorMsg = e.localizedMessage ?: e.javaClass.simpleName

            DiagnosticLogger.logHttpTransaction(
                method = method,
                url = urlString,
                statusCode = null,
                durationMs = durationMs,
                requestHeaders = requestHeaders,
                requestBody = requestBodySummary,
                isError = true,
                errorMessage = "Network Exception: $errorMsg",
                tag = determineTag(urlString)
            )

            throw e
        }
    }

    private fun extractRequestBody(request: Request): String? {
        val body = request.body ?: return null
        val contentType = body.contentType()?.toString() ?: ""

        // Do not dump raw multipart binary chunks to logs
        if (contentType.contains("multipart", ignoreCase = true) || contentType.contains("octet-stream", ignoreCase = true)) {
            return "[Binary/Multipart Payload - ${body.contentLength()} bytes]"
        }

        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            buffer.readString(charset).take(2000)
        } catch (e: Exception) {
            "[Unable to read request payload: ${e.message}]"
        }
    }

    private fun determineTag(url: String): String {
        return when {
            url.contains("/api/files/") -> "FILE_SEARCH_INFO"
            url.contains("/api/upload") -> "FILE_UPLOAD_CHUNK"
            url.contains("/api/health") -> "HEALTH_CHECK"
            url.contains("ntfy.sh") -> "CLOUD_RELAY_TOPIC"
            url.contains("catbox") || url.contains("tmpfiles") -> "CLOUD_STORAGE"
            else -> "RETROFIT_HTTP"
        }
    }
}
