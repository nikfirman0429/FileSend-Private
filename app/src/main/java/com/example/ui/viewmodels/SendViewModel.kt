package com.example.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FileSendApp
import com.example.data.model.ExpirationOption
import com.example.data.model.SelectedFileInfo
import com.example.data.model.TransferProgressState
import com.example.data.remote.NetworkResult
import com.example.data.repository.FileRepository
import com.example.engine.FileUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SendViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FileRepository = (application as FileSendApp).fileRepository
    private val appPreferences = (application as FileSendApp).appPreferences

    private val _selectedFile = MutableStateFlow<SelectedFileInfo?>(null)
    val selectedFile: StateFlow<SelectedFileInfo?> = _selectedFile.asStateFlow()

    private val _selectedExpiration = MutableStateFlow(appPreferences.getDefaultExpiration())
    val selectedExpiration: StateFlow<ExpirationOption> = _selectedExpiration.asStateFlow()

    private val _uploadState = MutableStateFlow<TransferProgressState>(TransferProgressState.Idle)
    val uploadState: StateFlow<TransferProgressState> = _uploadState.asStateFlow()

    private var activeUploadJob: Job? = null

    fun selectFile(uri: Uri) {
        val fileInfo = FileUtils.getSelectedFileInfo(getApplication(), uri)
        _selectedFile.value = fileInfo
        _uploadState.value = TransferProgressState.Idle
    }

    fun setExpiration(expiration: ExpirationOption) {
        _selectedExpiration.value = expiration
    }

    fun startUpload() {
        val file = _selectedFile.value ?: return
        if (activeUploadJob?.isActive == true) return

        activeUploadJob = viewModelScope.launch {
            _uploadState.value = TransferProgressState.CalculatingHash(
                fileName = file.name,
                progress = 0f
            )

            val uri = Uri.parse(file.uriString)
            val result = repository.uploadFile(
                uri = uri,
                fileName = file.name,
                fileSize = file.size,
                mimeType = file.mimeType,
                expiration = _selectedExpiration.value,
                onHashProgress = { hashProgress ->
                    _uploadState.value = TransferProgressState.CalculatingHash(
                        fileName = file.name,
                        progress = hashProgress
                    )
                },
                onProgress = { progress ->
                    _uploadState.value = TransferProgressState.Uploading(
                        fileName = progress.fileName,
                        progressPercent = progress.progressPercent,
                        uploadedBytes = progress.uploadedBytes,
                        totalBytes = progress.totalBytes,
                        currentChunk = progress.currentChunk,
                        totalChunks = progress.totalChunks,
                        speedBytesPerSec = progress.speedBytesPerSec,
                        etaSeconds = progress.etaSeconds
                    )
                }
            )

            when (result) {
                is NetworkResult.Success -> {
                    val entity = result.data
                    _uploadState.value = TransferProgressState.UploadSuccess(
                        code = entity.code,
                        fileName = entity.fileName,
                        size = entity.fileSize,
                        checksum = entity.checksum,
                        shareUrl = entity.shareUrl ?: "",
                        expiresAt = entity.expiresAt
                    )
                }
                is NetworkResult.Error -> {
                    _uploadState.value = TransferProgressState.Error(
                        message = result.message,
                        errorCode = result.statusCode
                    )
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun cancelUpload() {
        activeUploadJob?.cancel()
        activeUploadJob = null
        _uploadState.value = TransferProgressState.Cancelled("Upload was cancelled by user.")
    }

    fun resetState() {
        _uploadState.value = TransferProgressState.Idle
        _selectedFile.value = null
    }
}
