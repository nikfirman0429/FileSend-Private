package com.example.data.remote

import com.example.data.model.FileMetadataResponse
import com.example.data.model.GenericMessageResponse
import com.example.data.model.HealthResponse
import com.example.data.model.UploadChunkResponse
import com.example.data.model.UploadCompleteRequest
import com.example.data.model.UploadCompleteResponse
import com.example.data.model.UploadInitRequest
import com.example.data.model.UploadInitResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface FileSendApiService {

    @GET("api/health")
    suspend fun checkHealth(): Response<HealthResponse>

    @POST("api/upload/init")
    suspend fun initUpload(
        @Body request: UploadInitRequest
    ): Response<UploadInitResponse>

    @Multipart
    @POST("api/upload/chunk")
    suspend fun uploadChunk(
        @retrofit2.http.Header("X-Upload-Token") headerToken: String,
        @retrofit2.http.Header("X-Chunk-Index") headerIndex: Int,
        @Part("uploadToken") uploadToken: RequestBody,
        @Part("chunkIndex") chunkIndex: RequestBody,
        @Part chunk: MultipartBody.Part
    ): Response<UploadChunkResponse>

    @POST("api/upload/complete")
    suspend fun completeUpload(
        @Body request: UploadCompleteRequest
    ): Response<UploadCompleteResponse>

    @GET("api/files/{code}")
    suspend fun getFileInfo(
        @Path("code") code: String
    ): Response<FileMetadataResponse>

    @Streaming
    @GET("api/files/{code}/download")
    suspend fun downloadFileStream(
        @Path("code") code: String
    ): Response<ResponseBody>

    @DELETE("api/files/{code}")
    suspend fun deleteFile(
        @Path("code") code: String
    ): Response<GenericMessageResponse>
}
