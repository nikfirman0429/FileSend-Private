package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FileSendApp
import com.example.data.model.FileMetadataResponse
import com.example.data.model.TransferProgressState
import com.example.data.remote.NetworkResult
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FileRepository = (application as FileSendApp).fileRepository

    private val _inputCode = MutableStateFlow("")
    val inputCode: StateFlow<String> = _inputCode.asStateFlow()

    private val _queriedMetadata = MutableStateFlow<FileMetadataResponse?>(null)
    val queriedMetadata: StateFlow<FileMetadataResponse?> = _queriedMetadata.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _downloadState = MutableStateFlow<TransferProgressState>(TransferProgressState.Idle)
    val downloadState: StateFlow<TransferProgressState> = _downloadState.asStateFlow()

    private var activeDownloadJob: Job? = null

    fun setInputCode(code: String) {
        _inputCode.value = sanitizeCode(code)
        _searchError.value = null
    }

    fun findFile(code: String = _inputCode.value) {
        val cleanCode = sanitizeCode(code)
        if (cleanCode.isBlank()) {
            _searchError.value = "Please enter a valid 6-character file code (e.g. X7K92P)."
            return
        }

        _inputCode.value = cleanCode
        _isSearching.value = true
        _searchError.value = null
        _queriedMetadata.value = null
        _downloadState.value = TransferProgressState.Idle

        viewModelScope.launch {
            val result = repository.getFileInfo(cleanCode)
            _isSearching.value = false
            when (result) {
                is NetworkResult.Success -> {
                    _queriedMetadata.value = result.data
                }
                is NetworkResult.Error -> {
                    _searchError.value = result.message
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    companion object {
        fun sanitizeCode(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.contains("/") || trimmed.contains(".")) {
                // Extracts code from share URL like https://filesend.app/f/X7K92P
                val lastSegment = trimmed.removeSuffix("/").substringAfterLast("/")
                return lastSegment.filter { it.isLetterOrDigit() }.take(6).uppercase()
            }
            return trimmed.filter { it.isLetterOrDigit() }.take(6).uppercase()
        }
    }

    fun startDownload() {
        val metadata = _queriedMetadata.value ?: return
        if (activeDownloadJob?.isActive == true) return

        activeDownloadJob = viewModelScope.launch {
            _downloadState.value = TransferProgressState.Downloading(
                code = metadata.code,
                fileName = metadata.originalName,
                progressPercent = 0,
                downloadedBytes = 0,
                totalBytes = metadata.size,
                speedBytesPerSec = 0.0,
                etaSeconds = 0
            )

            val result = repository.downloadFile(
                metadata = metadata,
                onProgress = { progress ->
                    _downloadState.value = TransferProgressState.Downloading(
                        code = progress.code,
                        fileName = progress.fileName,
                        progressPercent = progress.progressPercent,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        speedBytesPerSec = progress.speedBytesPerSec,
                        etaSeconds = progress.etaSeconds
                    )
                }
            )

            when (result) {
                is NetworkResult.Success -> {
                    val entity = result.data
                    _downloadState.value = TransferProgressState.DownloadSuccess(
                        code = entity.code,
                        fileName = entity.fileName,
                        size = entity.fileSize,
                        checksum = entity.checksum,
                        localFilePath = entity.localFilePath ?: "",
                        isChecksumVerified = true
                    )
                }
                is NetworkResult.Error -> {
                    _downloadState.value = TransferProgressState.Error(
                        message = result.message,
                        errorCode = result.statusCode
                    )
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        _downloadState.value = TransferProgressState.Cancelled("Download was cancelled by user.")
    }

    fun resetState() {
        _downloadState.value = TransferProgressState.Idle
        _queriedMetadata.value = null
        _searchError.value = null
    }
}
