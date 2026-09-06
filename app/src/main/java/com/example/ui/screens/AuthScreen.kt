package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OnPrimaryPurpleContainer
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleContainer
import com.example.ui.theme.SecondaryLilacContainer
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekGreenContainer
import com.example.ui.theme.SleekRed
import com.example.ui.theme.SleekRedContainer
import com.example.ui.viewmodels.AuthViewModel
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isRegisterMode by viewModel.isRegisterMode.collectAsState()
    val emailInput by viewModel.emailInput.collectAsState()
    val passwordInput by viewModel.passwordInput.collectAsState()
    val confirmPasswordInput by viewModel.confirmPasswordInput.collectAsState()
    val displayNameInput by viewModel.displayNameInput.collectAsState()
    val passwordVisible by viewModel.passwordVisible.collectAsState()
    val confirmPasswordVisible by viewModel.confirmPasswordVisible.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    val showForgotPasswordDialog by viewModel.showForgotPasswordDialog.collectAsState()
    val forgotPasswordEmail by viewModel.forgotPasswordEmail.collectAsState()
    val isResettingPassword by viewModel.isResettingPassword.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentUser != null) {
                            "Account Details"
                        } else if (isRegisterMode) {
                            "Register Account"
                        } else {
                            "Sign In to FileSend"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    // Back button is only visible when user is authenticated and viewing account details
                    if (currentUser != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("auth_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (currentUser != null) {
                // User is already logged in -> show Profile & Account details
                LoggedInProfileContent(
                    user = currentUser!!,
                    onSignOut = {
                        viewModel.signOut(context)
                    },
                    onContinue = onAuthSuccess
                )
            } else {
                // User is NOT logged in -> mandatory Login / Registration forms
                AuthFormsContent(
                    isRegisterMode = isRegisterMode,
                    emailInput = emailInput,
                    passwordInput = passwordInput,
                    confirmPasswordInput = confirmPasswordInput,
                    displayNameInput = displayNameInput,
                    passwordVisible = passwordVisible,
                    confirmPasswordVisible = confirmPasswordVisible,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    infoMessage = infoMessage,
                    onSetRegisterMode = { viewModel.setRegisterMode(it) },
                    onEmailChange = { viewModel.setEmail(it) },
                    onPasswordChange = { viewModel.setPassword(it) },
                    onConfirmPasswordChange = { viewModel.setConfirmPassword(it) },
                    onDisplayNameChange = { viewModel.setDisplayName(it) },
                    onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
                    onToggleConfirmPasswordVisibility = { viewModel.toggleConfirmPasswordVisibility() },
                    onDismissError = { viewModel.clearError() },
                    onDismissInfo = { viewModel.clearInfo() },
                    onSubmitEmailAuth = {
                        if (isRegisterMode) {
                            viewModel.registerWithEmail(onSuccess = onAuthSuccess)
                        } else {
                            viewModel.signInWithEmail(onSuccess = onAuthSuccess)
                        }
                    },
                    onGoogleSignIn = {
                        viewModel.signInWithGoogle(context, onSuccess = onAuthSuccess)
                    },
                    onOpenForgotPassword = { viewModel.openForgotPasswordDialog() }
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissForgotPasswordDialog() },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your registered email address and we will send you a password reset link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = forgotPasswordEmail,
                        onValueChange = { viewModel.setForgotPasswordEmail(it) },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendPasswordReset() },
                    enabled = !isResettingPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    if (isResettingPassword) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send Link")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissForgotPasswordDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LoggedInProfileContent(
    user: FirebaseUser,
    onSignOut: () -> Unit,
    onContinue: () -> Unit
) {
    val isGoogleUser = user.providerData.any { it.providerId == "google.com" }
    val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "FileSend User"
    val email = user.email ?: "No email provided"
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString()
        ?: email.firstOrNull()?.uppercaseChar()?.toString()
        ?: "U"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_profile_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PrimaryPurpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = OnPrimaryPurpleContainer
                    )
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Provider Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isGoogleUser) SecondaryLilacContainer else PrimaryPurpleContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isGoogleUser) {
                    GoogleIconGraphic(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Signed in with Google",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryPurple
                    )
                    Text(
                        text = "Email & Password Account",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OnPrimaryPurpleContainer
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Account Details list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileDetailRow(
                    label = "Firebase UID",
                    value = user.uid.take(16) + "...",
                    isMonospace = true
                )
                ProfileDetailRow(
                    label = "Status",
                    value = if (user.isEmailVerified || isGoogleUser) "Verified" else "Active",
                    isPositive = true
                )
                ProfileDetailRow(
                    label = "Storage & Relay",
                    value = "Private Cloud Enabled"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Continue to main app
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("continue_to_app_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Continue to File Transfers", fontWeight = FontWeight.Bold)
            }

            // Sign Out Button
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekRed)
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    isPositive: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isMonospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            },
            color = if (isPositive) SleekGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AuthFormsContent(
    isRegisterMode: Boolean,
    emailInput: String,
    passwordInput: String,
    confirmPasswordInput: String,
    displayNameInput: String,
    passwordVisible: Boolean,
    confirmPasswordVisible: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onSetRegisterMode: (Boolean) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onDismissError: () -> Unit,
    onDismissInfo: () -> Unit,
    onSubmitEmailAuth: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onOpenForgotPassword: () -> Unit
) {
    // Header
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryPurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isRegisterMode) "Create an Account" else "Welcome to FileSend",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isRegisterMode) {
                "Create an account or use Google to start using FileSend"
            } else {
                "Sign in with your account or Google to access FileSend"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    // Tab Switcher: Sign In vs Register
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        TabRow(
            selectedTabIndex = if (isRegisterMode) 1 else 0,
            containerColor = Color.Transparent,
            contentColor = PrimaryPurple,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[if (isRegisterMode) 1 else 0]),
                    color = PrimaryPurple
                )
            },
            divider = {}
        ) {
            Tab(
                selected = !isRegisterMode,
                onClick = { onSetRegisterMode(false) },
                text = {
                    Text(
                        "Sign In",
                        fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.testTag("tab_sign_in")
            )
            Tab(
                selected = isRegisterMode,
                onClick = { onSetRegisterMode(true) },
                text = {
                    Text(
                        "Register",
                        fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.testTag("tab_register")
            )
        }
    }

    // Feedback Banners
    AnimatedVisibility(visible = errorMessage != null) {
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SleekRedContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = SleekRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = SleekRed,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        tint = SleekRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    AnimatedVisibility(visible = infoMessage != null) {
        if (infoMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SleekGreenContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SleekGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = infoMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = SleekGreen,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissInfo, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss message",
                        tint = SleekGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Google Sign-In Primary Button
    OutlinedButton(
        onClick = onGoogleSignIn,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("google_sign_in_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        GoogleIconGraphic(modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isRegisterMode) "Sign up with Google" else "Continue with Google",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }

    // Divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "OR",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }

    // Form Fields
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isRegisterMode) {
            OutlinedTextField(
                value = displayNameInput,
                onValueChange = onDisplayNameChange,
                label = { Text("Display Name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        OutlinedTextField(
            value = emailInput,
            onValueChange = onEmailChange,
            label = { Text("Email Address") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_input"),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_input"),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!isRegisterMode) onSubmitEmailAuth() }
            )
        )

        if (isRegisterMode) {
            OutlinedTextField(
                value = confirmPasswordInput,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = onToggleConfirmPasswordVisibility) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_password_input"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmitEmailAuth() }
                )
            )
        }

        if (!isRegisterMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onOpenForgotPassword,
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = PrimaryPurple,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Submit Button
        Button(
            onClick = onSubmitEmailAuth,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("auth_submit_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isRegisterMode) "Create Account" else "Sign In",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Standard multi-colored Google 'G' icon rendered in Compose Canvas.
 */
@Composable
fun GoogleIconGraphic(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = kotlin.math.min(w, h) / 2f

        // Google colors: Red (#EA4335), Yellow (#FBBC05), Green (#34A853), Blue (#4285F4)
        val blue = Color(0xFF4285F4)
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)

        // Draw outer colored sectors
        drawArc(
            color = red,
            startAngle = 180f,
            sweepAngle = 135f,
            useCenter = true
        )
        drawArc(
            color = yellow,
            startAngle = 135f,
            sweepAngle = 45f,
            useCenter = true
        )
        drawArc(
            color = green,
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = true
        )
        drawArc(
            color = blue,
            startAngle = 315f,
            sweepAngle = 90f,
            useCenter = true
        )

        // Inner circle hole to make the ring
        drawCircle(
            color = Color.White,
            radius = radius * 0.58f,
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )

        // Horizontal blue bar of G
        drawRect(
            color = blue,
            topLeft = androidx.compose.ui.geometry.Offset(cx - radius * 0.05f, cy - radius * 0.22f),
            size = androidx.compose.ui.geometry.Size(radius * 0.95f, radius * 0.44f)
        )
    }
}
