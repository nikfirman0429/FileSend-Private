package com.example.data.remote

import android.content.Context
import com.example.data.preferences.AppPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    private val context: Context,
    private val appPreferences: AppPreferences
) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(IPv4FirstDns)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(DiagnosticLoggingInterceptor())
        .addInterceptor(LocalRelayInterceptor(context))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    private var currentBaseUrl: String = ""
    private var cachedService: FileSendApiService? = null

    fun getService(): FileSendApiService {
        val serverUrl = appPreferences.getServerUrl().trim().removeSuffix("/") + "/"
        if (cachedService == null || currentBaseUrl != serverUrl) {
            currentBaseUrl = serverUrl
            val retrofit = Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            cachedService = retrofit.create(FileSendApiService::class.java)
        }
        return cachedService!!
    }

    companion object {
        fun parseErrorMessage(errorBody: String?, statusCode: Int): String {
            if (!errorBody.isNullOrBlank()) {
                try {
                    val json = JSONObject(errorBody)
                    if (json.has("error")) {
                        return json.getString("error")
                    } else if (json.has("message")) {
                        return json.getString("message")
                    }
                } catch (e: Exception) {
                    // ignore JSON parsing exception
                }
            }

            return when (statusCode) {
                400 -> "Invalid request. Please check input details."
                401 -> "Unauthorized. Authentication failed."
                403 -> "Access denied to this file."
                404 -> "File was not found or has been removed."
                408 -> "Request timed out. Please check network connection."
                409 -> "Integrity check failed: Checksum mismatch."
                410 -> "This file has expired and is no longer available."
                413 -> "File size exceeds server upload limit."
                429 -> "Too many requests. Please wait a moment."
                500 -> "Server encountered an internal error. Please try again."
                502 -> "Bad gateway: Server unavailable."
                503 -> "Server is currently unavailable or under maintenance."
                504 -> "Gateway timeout. Please check your internet connection."
                else -> "Network operation failed (HTTP $statusCode)."
            }
        }
    }
}
