package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ExpirationOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _serverUrlFlow = MutableStateFlow(getServerUrl())
    val serverUrlFlow: StateFlow<String> = _serverUrlFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    fun getServerUrl(): String {
        val saved = prefs.getString(KEY_SERVER_URL, null)
        if (saved.isNullOrBlank() || saved == "https://YOUR_DOMAIN" || saved.contains("YOUR_DOMAIN")) {
            return DEFAULT_SERVER_URL
        }
        return saved
    }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply()
        _serverUrlFlow.value = cleanUrl
    }

    fun getDefaultExpiration(): ExpirationOption {
        val hours = prefs.getInt(KEY_DEFAULT_EXPIRATION, 24)
        return ExpirationOption.values().find { it.hours == hours } ?: ExpirationOption.TWENTY_FOUR_HOURS
    }

    fun setDefaultExpiration(option: ExpirationOption) {
        prefs.edit().putInt(KEY_DEFAULT_EXPIRATION, option.hours).apply()
    }

    fun getChunkSizeBytes(): Long {
        return prefs.getLong(KEY_CHUNK_SIZE, 4 * 1024 * 1024L) // 4 MB default
    }

    fun setChunkSizeBytes(size: Long) {
        prefs.edit().putLong(KEY_CHUNK_SIZE, size).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    companion object {
        private const val PREFS_NAME = "filesend_private_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEFAULT_EXPIRATION = "default_expiration"
        private const val KEY_CHUNK_SIZE = "chunk_size"
        private const val KEY_THEME_MODE = "theme_mode"

        // Default functional server URL (FileSend Private In-App Relay)
        const val DEFAULT_SERVER_URL = "https://filesend-private.local"
    }
}
