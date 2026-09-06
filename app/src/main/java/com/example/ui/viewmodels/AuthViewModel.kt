package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FileSendApp
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = (application as FileSendApp).authRepository

    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUser

    private val _isRegisterMode = MutableStateFlow(false)
    val isRegisterMode: StateFlow<Boolean> = _isRegisterMode.asStateFlow()

    private val _emailInput = MutableStateFlow("")
    val emailInput: StateFlow<String> = _emailInput.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _confirmPasswordInput = MutableStateFlow("")
    val confirmPasswordInput: StateFlow<String> = _confirmPasswordInput.asStateFlow()

    private val _displayNameInput = MutableStateFlow("")
    val displayNameInput: StateFlow<String> = _displayNameInput.asStateFlow()

    private val _passwordVisible = MutableStateFlow(false)
    val passwordVisible: StateFlow<Boolean> = _passwordVisible.asStateFlow()

    private val _confirmPasswordVisible = MutableStateFlow(false)
    val confirmPasswordVisible: StateFlow<Boolean> = _confirmPasswordVisible.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    private val _showForgotPasswordDialog = MutableStateFlow(false)
    val showForgotPasswordDialog: StateFlow<Boolean> = _showForgotPasswordDialog.asStateFlow()

    private val _forgotPasswordEmail = MutableStateFlow("")
    val forgotPasswordEmail: StateFlow<String> = _forgotPasswordEmail.asStateFlow()

    private val _isResettingPassword = MutableStateFlow(false)
    val isResettingPassword: StateFlow<Boolean> = _isResettingPassword.asStateFlow()

    fun setRegisterMode(register: Boolean) {
        _isRegisterMode.value = register
        _errorMessage.value = null
        _infoMessage.value = null
    }

    fun setEmail(value: String) {
        _emailInput.value = value
        _errorMessage.value = null
    }

    fun setPassword(value: String) {
        _passwordInput.value = value
        _errorMessage.value = null
    }

    fun setConfirmPassword(value: String) {
        _confirmPasswordInput.value = value
        _errorMessage.value = null
    }

    fun setDisplayName(value: String) {
        _displayNameInput.value = value
        _errorMessage.value = null
    }

    fun togglePasswordVisibility() {
        _passwordVisible.value = !_passwordVisible.value
    }

    fun toggleConfirmPasswordVisibility() {
        _confirmPasswordVisible.value = !_confirmPasswordVisible.value
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearInfo() {
        _infoMessage.value = null
    }

    fun openForgotPasswordDialog() {
        _forgotPasswordEmail.value = _emailInput.value
        _showForgotPasswordDialog.value = true
        _errorMessage.value = null
    }

    fun dismissForgotPasswordDialog() {
        _showForgotPasswordDialog.value = false
    }

    fun setForgotPasswordEmail(email: String) {
        _forgotPasswordEmail.value = email
    }

    fun sendPasswordReset() {
        val email = _forgotPasswordEmail.value.trim()
        if (email.isEmpty()) {
            _errorMessage.value = "Please enter your email to receive a password reset link."
            return
        }

        viewModelScope.launch {
            _isResettingPassword.value = true
            when (val result = authRepository.sendPasswordReset(email)) {
                is AuthResult.Success -> {
                    _showForgotPasswordDialog.value = false
                    _infoMessage.value = "Password reset email sent to $email. Please check your inbox."
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {}
            }
            _isResettingPassword.value = false
        }
    }

    fun signInWithEmail(onSuccess: () -> Unit) {
        val email = _emailInput.value.trim()
        val password = _passwordInput.value

        if (email.isEmpty()) {
            _errorMessage.value = "Please enter your email address."
            return
        }
        if (password.isEmpty()) {
            _errorMessage.value = "Please enter your password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            when (val result = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {
                    _isLoading.value = false
                }
            }
        }
    }

    fun registerWithEmail(onSuccess: () -> Unit) {
        val email = _emailInput.value.trim()
        val password = _passwordInput.value
        val confirmPassword = _confirmPasswordInput.value
        val displayName = _displayNameInput.value.trim()

        if (email.isEmpty()) {
            _errorMessage.value = "Please enter your email address."
            return
        }
        if (password.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters."
            return
        }
        if (password != confirmPassword) {
            _errorMessage.value = "Passwords do not match."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            when (val result = authRepository.registerWithEmail(email, password, displayName)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {
                    _isLoading.value = false
                }
            }
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            when (val result = authRepository.signInWithGoogle(context)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {
                    _isLoading.value = false
                }
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
            _emailInput.value = ""
            _passwordInput.value = ""
            _confirmPasswordInput.value = ""
            _displayNameInput.value = ""
            _errorMessage.value = null
            _infoMessage.value = "Successfully signed out."
        }
    }
}
