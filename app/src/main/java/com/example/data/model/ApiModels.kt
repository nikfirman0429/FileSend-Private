package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UploadInitRequest(
    @Json(name = "originalName") val originalName: String,
    @Json(name = "size") val size: Long,
    @Json(name = "totalChunks") val totalChunks: Int,
    @Json(name = "checksum") val checksum: String,
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "expirationHours") val expirationHours: Int
)

@JsonClass(generateAdapter = true)
data class UploadInitResponse(
    @Json(name = "uploadToken") val uploadToken: String,
    @Json(name = "code") val code: String,
    @Json(name = "shareUrl") val shareUrl: String,
    @Json(name = "chunkSize") val chunkSize: Long,
    @Json(name = "expiresAt") val expiresAt: Long?
)

@JsonClass(generateAdapter = true)
data class UploadChunkResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "chunkIndex") val chunkIndex: Int,
    @Json(name = "receivedChunks") val receivedChunks: Int,
    @Json(name = "totalChunks") val totalChunks: Int
)

@JsonClass(generateAdapter = true)
data class UploadCompleteRequest(
    @Json(name = "uploadToken") val uploadToken: String
)

@JsonClass(generateAdapter = true)
data class UploadCompleteResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "code") val code: String,
    @Json(name = "originalName") val originalName: String,
    @Json(name = "size") val size: Long,
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "checksum") val checksum: String,
    @Json(name = "shareUrl") val shareUrl: String,
    @Json(name = "downloadUrl") val downloadUrl: String,
    @Json(name = "expiresAt") val expiresAt: Long?
)

@JsonClass(generateAdapter = true)
data class FileMetadataResponse(
    @Json(name = "code") val code: String,
    @Json(name = "originalName") val originalName: String,
    @Json(name = "extension") val extension: String,
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "size") val size: Long,
    @Json(name = "checksum") val checksum: String,
    @Json(name = "createdAt") val createdAt: Long,
    @Json(name = "expiresAt") val expiresAt: Long?,
    @Json(name = "downloadCount") val downloadCount: Int,
    @Json(name = "shareUrl") val shareUrl: String,
    @Json(name = "downloadUrl") val downloadUrl: String
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String,
    @Json(name = "service") val service: String,
    @Json(name = "version") val version: String,
    @Json(name = "privateMode") val privateMode: Boolean,
    @Json(name = "maxFileSizeBytes") val maxFileSizeBytes: Long,
    @Json(name = "serverTime") val serverTime: Long
)

@JsonClass(generateAdapter = true)
data class GenericMessageResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "error") val error: String? = null
)
