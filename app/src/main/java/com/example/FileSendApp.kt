package com.example

import android.app.Application
import com.example.data.auth.AuthRepository
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.remote.ApiClient
import com.example.data.repository.FileRepository
import com.google.android.gms.ads.MobileAds

class FileSendApp : Application() {

    lateinit var appPreferences: AppPreferences
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var fileRepository: FileRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Google Mobile Ads SDK asynchronously
        try {
            MobileAds.initialize(this) {}
        } catch (_: Exception) {}

        appPreferences = AppPreferences(this)
        apiClient = ApiClient(this, appPreferences)
        database = AppDatabase.getDatabase(this)
        authRepository = AuthRepository()
        fileRepository = FileRepository(
            context = this,
            apiClient = apiClient,
            historyDao = database.transferHistoryDao(),
            appPreferences = appPreferences
        )
    }

    companion object {
        lateinit var instance: FileSendApp
            private set
    }
}
