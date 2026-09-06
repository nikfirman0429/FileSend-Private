package com.example.data.remote

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CloudRelayBridge manages cross-device global uploads and metadata synchronization.
 * It integrates Firebase Cloud Firestore & Realtime Database for instantaneous global resolution,
 * and high-speed multi-provider cloud storage so ANY device entering the 6-character code
 * can immediately find and download the exact file.
 */
object CloudRelayBridge {

    private const val TAG = "CloudRelayBridge"
    private const val PROJECT_ID = "filesendprivate"
    private const val FIRESTORE_BASE = "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/transfers"
    private const val FIREBASE_RTDB_BASE = "https://filesendprivate-default-rtdb.firebaseio.com/transfers"

    private fun logD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (e: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logW(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (e: Throwable) {
            System.err.println("[$TAG] $msg")
        }
    }

    private fun getFirebaseAuthToken(): String? {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val task = user.getIdToken(false)
                Tasks.await(task, 2, TimeUnit.SECONDS)?.token
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val fastQueryClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    data class CloudUploadResult(
        val success: Boolean,
        val downloadUrl: String,
        val provider: String,
        val errorMessage: String? = null
    )

    /**
     * Uploads the assembled file to global high-speed cloud storage.
     * Uses Litterbox (Catbox), TmpFiles, and File.io with seamless failover.
     */
    fun uploadFileToGlobalCloud(file: File, mimeType: String, expirationHours: Int): CloudUploadResult {
        // Attempt Provider 1: Litterbox Catbox (supports up to 1GB, direct high-speed CDN)
        try {
            val timeParam = when {
                expirationHours <= 1 -> "1h"
                expirationHours <= 12 -> "12h"
                expirationHours <= 24 -> "24h"
                else -> "72h"
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("time", timeParam)
                .addFormDataPart(
                    "fileToUpload",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://litterbox.catbox.moe/resources/internals/api.php")
                .post(requestBody)
                .header("User-Agent", "FileSendPrivate-Android/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val statusCode = response.code
            val responseBody = response.body?.string()?.trim() ?: ""

            DiagnosticLogger.logHttpTransaction(
                method = "POST",
                url = "https://litterbox.catbox.moe/resources/internals/api.php",
                statusCode = statusCode,
                durationMs = 0L,
                responseBody = responseBody,
                isError = !response.isSuccessful || (!responseBody.startsWith("http://") && !responseBody.startsWith("https://")),
                tag = "CLOUD_STORAGE_LITTERBOX"
            )

            if (response.isSuccessful) {
                if (responseBody.startsWith("http://") || responseBody.startsWith("https://")) {
                    logD("Litterbox upload succeeded: $responseBody")
                    return CloudUploadResult(
                        success = true,
                        downloadUrl = responseBody,
                        provider = "Litterbox"
                    )
                }
            }
        } catch (e: Exception) {
            logW("Litterbox upload failed, attempting fallback: ${e.message}")
        }

        // Attempt Provider 2: TmpFiles.org (fallback)
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://tmpfiles.org/api/v1/upload")
                .post(requestBody)
                .header("User-Agent", "FileSendPrivate-Android/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val statusCode = response.code
            val bodyStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                if (json.optString("status") == "success") {
                    val rawUrl = json.getJSONObject("data").getString("url")
                    val directUrl = if (rawUrl.contains("tmpfiles.org/") && !rawUrl.contains("tmpfiles.org/dl/")) {
                        rawUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                    } else rawUrl

                    logD("TmpFiles upload succeeded: $directUrl")
                    return CloudUploadResult(
                        success = true,
                        downloadUrl = directUrl,
                        provider = "TmpFiles"
                    )
                }
            }
        } catch (e: Exception) {
            logW("TmpFiles upload failed: ${e.message}")
        }

        // Attempt Provider 3: File.io (fallback 3)
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val expDays = (expirationHours / 24).coerceAtLeast(1)
            val request = Request.Builder()
                .url("https://file.io/?expires=${expDays}d")
                .post(requestBody)
                .header("User-Agent", "FileSendPrivate-Android/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""

            if (response.isSuccessful && bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                if (json.optBoolean("success", false) || json.has("link")) {
                    val link = json.optString("link", "")
                    if (link.isNotBlank()) {
                        logD("File.io upload succeeded: $link")
                        return CloudUploadResult(
                            success = true,
                            downloadUrl = link,
                            provider = "File.io"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logW("File.io upload failed: ${e.message}")
        }

        return CloudUploadResult(
            success = false,
            downloadUrl = "",
            provider = "LocalOnly",
            errorMessage = "Global cloud replication completed locally."
        )
    }

    /**
     * Publishes file transfer metadata to Firebase Firestore, Realtime Database & Global Relays
     * so ANY receiver with the 6-character code can immediately find it.
     */
    fun publishMetadata(
        code: String,
        originalName: String,
        size: Long,
        mimeType: String,
        checksum: String,
        downloadUrl: String,
        expiresAt: Long?
    ) {
        val cleanCode = code.trim().uppercase()
        val now = System.currentTimeMillis()

        val metaJson = JSONObject().apply {
            put("code", cleanCode)
            put("originalName", originalName)
            put("extension", if (originalName.contains(".")) originalName.substringAfterLast(".") else "")
            put("size", size)
            put("mimeType", mimeType)
            put("checksum", checksum)
            put("downloadUrl", downloadUrl)
            put("shareUrl", "https://filesend.app/f/$cleanCode")
            put("createdAt", now)
            if (expiresAt != null) put("expiresAt", expiresAt) else put("expiresAt", JSONObject.NULL)
            put("downloadCount", 0)
        }

        val jsonString = metaJson.toString()
        val token = getFirebaseAuthToken()

        // 1. Primary: Publish to Firebase Firestore REST API
        try {
            val firestoreUrl = "$FIRESTORE_BASE/$cleanCode"
            val fieldsObj = JSONObject().apply {
                put("code", JSONObject().put("stringValue", cleanCode))
                put("originalName", JSONObject().put("stringValue", originalName))
                put("extension", JSONObject().put("stringValue", if (originalName.contains(".")) originalName.substringAfterLast(".") else ""))
                put("size", JSONObject().put("integerValue", size.toString()))
                put("mimeType", JSONObject().put("stringValue", mimeType))
                put("checksum", JSONObject().put("stringValue", checksum))
                put("downloadUrl", JSONObject().put("stringValue", downloadUrl))
                put("shareUrl", JSONObject().put("stringValue", "https://filesend.app/f/$cleanCode"))
                put("createdAt", JSONObject().put("integerValue", now.toString()))
                if (expiresAt != null) {
                    put("expiresAt", JSONObject().put("integerValue", expiresAt.toString()))
                }
            }
            val firestoreBody = JSONObject().put("fields", fieldsObj).toString()

            val reqBuilder = Request.Builder()
                .url(firestoreUrl)
                .patch(firestoreBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))

            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            val codeResp = resp.code
            resp.close()

            DiagnosticLogger.logHttpTransaction(
                method = "PATCH",
                url = firestoreUrl,
                statusCode = codeResp,
                durationMs = 0L,
                requestBody = firestoreBody,
                responseBody = "OK ($codeResp)",
                isError = !resp.isSuccessful,
                tag = "FIRESTORE_PUBLISH"
            )
            logD("Published metadata to Firestore for code $cleanCode (HTTP $codeResp)")
        } catch (e: Exception) {
            logW("Firestore publish for $cleanCode: ${e.message}")
        }

        // 2. Secondary: Publish directly to Firebase Realtime Database
        try {
            val authQuery = if (!token.isNullOrBlank()) "?auth=$token" else ""
            val firebaseUrl = "$FIREBASE_RTDB_BASE/$cleanCode.json$authQuery"
            val request = Request.Builder()
                .url(firebaseUrl)
                .put(jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            val resp = httpClient.newCall(request).execute()
            val statusCode = resp.code
            resp.close()
            logD("Published metadata to Firebase RTDB for code $cleanCode (HTTP $statusCode)")
        } catch (e: Exception) {
            logW("Firebase RTDB publish for $cleanCode: ${e.message}")
        }

        // 3. Backup: Publish to ntfy cloud topic
        try {
            val topicUrl = "https://ntfy.sh/filesend_v1_$cleanCode"
            val request = Request.Builder()
                .url(topicUrl)
                .post(jsonString.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull()))
                .header("Title", "FileSend Metadata $cleanCode")
                .header("X-Cache", "1")
                .header("X-Poll", "1")
                .build()

            val resp = httpClient.newCall(request).execute()
            resp.close()
        } catch (e: Exception) {
            // ignore backup failure
        }
    }

    /**
     * Resolves metadata for a 6-character code from Firebase Firestore, RTDB, and Global Relays.
     * Executes lookups with fast timeouts to ensure responsive 6-character code resolution.
     */
    fun fetchMetadata(code: String): JSONObject? {
        val cleanCode = code.trim().uppercase()
        val token = getFirebaseAuthToken()

        DiagnosticLogger.logInfo(
            tag = "CLOUD_RELAY_FETCH",
            message = "Starting Cloud Relay metadata lookup for 6-character code: $cleanCode"
        )

        // 1. Primary: Query Firebase Firestore REST API
        try {
            val firestoreUrl = "$FIRESTORE_BASE/$cleanCode"
            val reqBuilder = Request.Builder()
                .url(firestoreUrl)
                .get()

            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val response = fastQueryClient.newCall(reqBuilder.build()).execute()
            val statusCode = response.code
            val bodyText = response.body?.string()?.trim() ?: ""

            DiagnosticLogger.logHttpTransaction(
                method = "GET",
                url = firestoreUrl,
                statusCode = statusCode,
                durationMs = 0L,
                responseBody = bodyText,
                isError = !response.isSuccessful || bodyText.isBlank(),
                tag = "FIRESTORE_FETCH"
            )

            if (response.isSuccessful && bodyText.isNotBlank()) {
                val parsed = parseFirestoreDocument(bodyText, cleanCode)
                if (parsed != null) {
                    logD("Metadata successfully found in Firestore for code $cleanCode")
                    DiagnosticLogger.logInfo(
                        tag = "FIRESTORE_FETCH",
                        message = "Resolved metadata for $cleanCode from Firestore: ${parsed.optString("originalName")} (${parsed.optLong("size")} bytes)"
                    )
                    return parsed
                }
            }
        } catch (e: Exception) {
            logW("Firestore lookup skipped/failed for $cleanCode: ${e.message}")
        }

        // 2. Secondary: Query Firebase Realtime Database
        try {
            val authQuery = if (!token.isNullOrBlank()) "?auth=$token" else ""
            val firebaseUrl = "$FIREBASE_RTDB_BASE/$cleanCode.json$authQuery"
            val request = Request.Builder()
                .url(firebaseUrl)
                .get()
                .build()

            val response = fastQueryClient.newCall(request).execute()
            val statusCode = response.code
            val bodyText = response.body?.string()?.trim() ?: ""

            DiagnosticLogger.logHttpTransaction(
                method = "GET",
                url = firebaseUrl,
                statusCode = statusCode,
                durationMs = 0L,
                responseBody = bodyText,
                isError = !response.isSuccessful || bodyText == "null" || bodyText.isBlank(),
                tag = "FIREBASE_RTDB_FETCH"
            )

            if (response.isSuccessful && bodyText.isNotBlank() && bodyText != "null") {
                val parsed = JSONObject(bodyText)
                if (parsed.has("code") || parsed.has("originalName") || parsed.has("downloadUrl")) {
                    logD("Metadata successfully found in Firebase RTDB for code $cleanCode")
                    return parsed
                }
            }
        } catch (e: Exception) {
            logW("Firebase RTDB lookup failed for $cleanCode: ${e.message}")
        }

        // 3. Backup: Query ntfy cache (instant poll)
        try {
            val queryUrl = "https://ntfy.sh/filesend_v1_$cleanCode/json?poll=1&since=all"
            val request = Request.Builder()
                .url(queryUrl)
                .get()
                .build()

            val response = fastQueryClient.newCall(request).execute()
            val bodyText = response.body?.string()?.trim() ?: ""
            if (response.isSuccessful && bodyText.isNotBlank()) {
                val parsed = parseMetadataFromResponse(bodyText, cleanCode)
                if (parsed != null) {
                    logD("Metadata resolved from ntfy cache for $cleanCode")
                    return parsed
                }
            }
        } catch (e: Exception) {
            // ignore backup failure
        }

        DiagnosticLogger.logError(
            tag = "CLOUD_RELAY_FETCH",
            message = "Metadata for 6-character code '$cleanCode' was not found."
        )
        return null
    }

    private fun parseFirestoreDocument(docJsonStr: String, expectedCode: String): JSONObject? {
        return try {
            val root = JSONObject(docJsonStr)
            if (!root.has("fields")) return null
            val fields = root.getJSONObject("fields")

            val code = fields.optJSONObject("code")?.optString("stringValue") ?: expectedCode
            val origName = fields.optJSONObject("originalName")?.optString("stringValue") ?: "file"
            val ext = fields.optJSONObject("extension")?.optString("stringValue") ?: ""
            val sizeStr = fields.optJSONObject("size")?.optString("integerValue") ?: "0"
            val mime = fields.optJSONObject("mimeType")?.optString("stringValue") ?: "application/octet-stream"
            val checksum = fields.optJSONObject("checksum")?.optString("stringValue") ?: ""
            val downloadUrl = fields.optJSONObject("downloadUrl")?.optString("stringValue") ?: ""
            val shareUrl = fields.optJSONObject("shareUrl")?.optString("stringValue") ?: "https://filesend.app/f/$expectedCode"
            val createdStr = fields.optJSONObject("createdAt")?.optString("integerValue") ?: System.currentTimeMillis().toString()
            val expStr = fields.optJSONObject("expiresAt")?.optString("integerValue")

            JSONObject().apply {
                put("code", code)
                put("originalName", origName)
                put("extension", ext)
                put("size", sizeStr.toLongOrNull() ?: 0L)
                put("mimeType", mime)
                put("checksum", checksum)
                put("downloadUrl", downloadUrl)
                put("shareUrl", shareUrl)
                put("createdAt", createdStr.toLongOrNull() ?: System.currentTimeMillis())
                if (expStr != null) put("expiresAt", expStr.toLongOrNull()) else put("expiresAt", JSONObject.NULL)
                put("downloadCount", 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMetadataFromResponse(responseBody: String, expectedCode: String): JSONObject? {
        val lines = responseBody.lines()
        for (line in lines.reversed()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            try {
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    val obj = JSONObject(trimmed)
                    if (obj.has("event") && obj.has("message")) {
                        val innerMsg = obj.getString("message").trim()
                        if (innerMsg.startsWith("{") && innerMsg.endsWith("}")) {
                            val innerJson = JSONObject(innerMsg)
                            if (innerJson.optString("code").equals(expectedCode, ignoreCase = true) ||
                                innerJson.has("downloadUrl") || innerJson.has("originalName")) {
                                return innerJson
                            }
                        }
                    } else if (obj.optString("code").equals(expectedCode, ignoreCase = true) ||
                        obj.has("downloadUrl") || obj.has("originalName")) {
                        return obj
                    }
                }
            } catch (e: Exception) {
                // Continue
            }
        }
        return null
    }
}

