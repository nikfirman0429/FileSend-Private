package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FileSendApp
import com.example.data.model.ExpirationOption
import com.example.data.preferences.AppPreferences
import com.example.data.remote.NetworkResult
import com.example.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ConnectionTestStatus {
    object Idle : ConnectionTestStatus()
    object Testing : ConnectionTestStatus()
    data class Success(val message: String) : ConnectionTestStatus()
    data class Error(val message: String) : ConnectionTestStatus()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: AppPreferences = (application as FileSendApp).appPreferences
    private val repository: FileRepository = (application as FileSendApp).fileRepository

    private val _serverUrlInput = MutableStateFlow(preferences.getServerUrl())
    val serverUrlInput: StateFlow<String> = _serverUrlInput.asStateFlow()

    private val _defaultExpiration = MutableStateFlow(preferences.getDefaultExpiration())
    val defaultExpiration: StateFlow<ExpirationOption> = _defaultExpiration.asStateFlow()

    private val _chunkSizeBytes = MutableStateFlow(preferences.getChunkSizeBytes())
    val chunkSizeBytes: StateFlow<Long> = _chunkSizeBytes.asStateFlow()

    private val _themeMode = MutableStateFlow(preferences.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _testStatus = MutableStateFlow<ConnectionTestStatus>(ConnectionTestStatus.Idle)
    val testStatus: StateFlow<ConnectionTestStatus> = _testStatus.asStateFlow()

    fun updateServerUrl(url: String) {
        _serverUrlInput.value = url
        _testStatus.value = ConnectionTestStatus.Idle
    }

    fun saveServerUrl() {
        preferences.setServerUrl(_serverUrlInput.value)
    }

    fun setDefaultExpiration(option: ExpirationOption) {
        preferences.setDefaultExpiration(option)
        _defaultExpiration.value = option
    }

    fun setChunkSizeBytes(bytes: Long) {
        preferences.setChunkSizeBytes(bytes)
        _chunkSizeBytes.value = bytes
    }

    fun setThemeMode(mode: String) {
        preferences.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun testConnection() {
        saveServerUrl()
        _testStatus.value = ConnectionTestStatus.Testing
        viewModelScope.launch {
            val result = repository.checkHealth()
            when (result) {
                is NetworkResult.Success -> {
                    val res = result.data
                    _testStatus.value = ConnectionTestStatus.Success(
                        "Connected to ${res.service} (v${res.version}) - Status: ${res.status.uppercase()}"
                    )
                }
                is NetworkResult.Error -> {
                    _testStatus.value = ConnectionTestStatus.Error(result.message)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }
}
