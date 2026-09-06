package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult<Nothing>()
    object Cancelled : AuthResult<Nothing>()
}

class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        const val GOOGLE_SERVER_CLIENT_ID = "431395495178-d8m2qlm7r79la2l5let18rm5c5dgds64.apps.googleusercontent.com"
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
        }
    }

    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    suspend fun signInWithEmail(email: String, password: String): AuthResult<FirebaseUser> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return AuthResult.Error("Please enter your email address.")
        }
        if (password.isEmpty()) {
            return AuthResult.Error("Please enter your password.")
        }

        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).awaitResult()
            val user = result.user ?: return AuthResult.Error("Sign in succeeded but user profile was not found.")
            _currentUser.value = user
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthErrorMessage(e), e)
        }
    }

    suspend fun registerWithEmail(email: String, password: String, displayName: String): AuthResult<FirebaseUser> {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()

        if (trimmedEmail.isEmpty()) {
            return AuthResult.Error("Please enter an email address.")
        }
        if (password.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters.")
        }

        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).awaitResult()
            val user = result.user ?: return AuthResult.Error("Registration succeeded but user profile was not found.")

            if (trimmedName.isNotEmpty()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                user.updateProfile(profileUpdates).awaitResult()
                user.reload().awaitResult()
            }

            _currentUser.value = firebaseAuth.currentUser
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthErrorMessage(e), e)
        }
    }

    suspend fun signInWithGoogle(context: Context): AuthResult<FirebaseUser> {
        val activityContext = findActivity(context) ?: context
        val credentialManager = CredentialManager.create(activityContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(GOOGLE_SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).awaitResult()
                val user = authResult.user ?: return AuthResult.Error("Google Sign-In succeeded but user profile was not found.")
                _currentUser.value = user
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Unsupported credential type received from Google Sign-In.")
            }
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (e: GetCredentialException) {
            AuthResult.Error(e.localizedMessage ?: "Google Sign-In failed. Please try again.", e)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthErrorMessage(e), e)
        }
    }

    suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return AuthResult.Error("Please enter your email address to reset password.")
        }

        return try {
            firebaseAuth.sendPasswordResetEmail(trimmedEmail).awaitResult()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthErrorMessage(e), e)
        }
    }

    suspend fun signOut(context: Context) {
        try {
            firebaseAuth.signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Best effort clear
        } finally {
            _currentUser.value = null
        }
    }

    private fun parseAuthErrorMessage(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "No account found with this email address."
            is FirebaseAuthInvalidCredentialsException -> "Invalid credentials. Please verify your email and password."
            is FirebaseAuthUserCollisionException -> "An account with this email address already exists."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use at least 6 characters."
            is FirebaseNetworkException -> "Network connection error. Please check your internet connection."
            else -> e.localizedMessage ?: "An unexpected authentication error occurred."
        }
    }
}

// Extension function to safely await Task<T> without external play-services-coroutines dependency
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
