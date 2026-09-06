package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DownloadCompleteScreen
import com.example.ui.screens.DownloadProgressScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiveFileScreen
import com.example.ui.screens.SendFileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadCompleteScreen
import com.example.ui.screens.UploadProgressScreen
import com.example.ui.theme.FileSendTheme
import com.example.ui.viewmodels.AuthViewModel
import com.example.ui.viewmodels.HistoryViewModel
import com.example.ui.viewmodels.ReceiveViewModel
import com.example.ui.viewmodels.SendViewModel
import com.example.ui.viewmodels.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val sendViewModel: SendViewModel by viewModels()
    private val receiveViewModel: ReceiveViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deepLinkCode = extractDeepLinkCode(intent)

        setContent {
            FileSendTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileSendAppNavigation(
                        sendViewModel = sendViewModel,
                        receiveViewModel = receiveViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel,
                        authViewModel = authViewModel,
                        initialDeepLinkCode = deepLinkCode
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val code = extractDeepLinkCode(intent)
        if (!code.isNullOrBlank()) {
            receiveViewModel.setInputCode(code)
            receiveViewModel.findFile(code)
        }
    }

    private fun extractDeepLinkCode(intent: Intent?): String? {
        val data: Uri? = intent?.data
        if (data != null) {
            // Check scheme filesend://f/CODE or filesend://CODE
            if (data.scheme == "filesend") {
                return data.lastPathSegment ?: data.host
            }
            // Check https://DOMAIN/f/CODE
            val pathSegments = data.pathSegments
            if (pathSegments.size >= 2 && pathSegments[0] == "f") {
                return pathSegments[1]
            }
        }
        return null
    }
}

@Composable
fun FileSendAppNavigation(
    sendViewModel: SendViewModel,
    receiveViewModel: ReceiveViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    initialDeepLinkCode: String?
) {
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsState()

    // Determine starting destination based on authentication state
    val startDestination = if (currentUser != null) Screen.Home.route else Screen.Auth.route

    // If user signs out, immediately revoke access and navigate to Auth screen
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != null && currentRoute != Screen.Auth.route) {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(initialDeepLinkCode, currentUser) {
        if (!initialDeepLinkCode.isNullOrBlank() && currentUser != null) {
            receiveViewModel.setInputCode(initialDeepLinkCode)
            receiveViewModel.findFile(initialDeepLinkCode)
            navController.navigate("${Screen.ReceiveFile.route}?code=$initialDeepLinkCode") {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                currentUser = currentUser,
                onNavigateToSend = {
                    navController.navigate(Screen.SendFile.route)
                },
                onNavigateToReceive = { code ->
                    if (code != null) {
                        navController.navigate("${Screen.ReceiveFile.route}?code=$code")
                    } else {
                        navController.navigate(Screen.ReceiveFile.route)
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route)
                }
            )
        }

        composable(Screen.SendFile.route) {
            SendFileScreen(
                viewModel = sendViewModel,
                onBack = { navController.popBackStack() },
                onStartUpload = {
                    navController.navigate(Screen.UploadProgress.route)
                }
            )
        }

        composable(Screen.UploadProgress.route) {
            UploadProgressScreen(
                viewModel = sendViewModel,
                onBack = {
                    navController.popBackStack(Screen.SendFile.route, inclusive = false)
                },
                onUploadSuccess = {
                    navController.navigate(Screen.UploadComplete.route) {
                        popUpTo(Screen.SendFile.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.UploadComplete.route) {
            UploadCompleteScreen(
                viewModel = sendViewModel,
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Screen.ReceiveFile.route}?code={code}",
            arguments = listOf(
                navArgument("code") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val codeArg = backStackEntry.arguments?.getString("code")
            ReceiveFileScreen(
                viewModel = receiveViewModel,
                initialCode = codeArg,
                onBack = { navController.popBackStack() },
                onStartDownload = {
                    navController.navigate(Screen.DownloadProgress.route)
                }
            )
        }

        composable(Screen.DownloadProgress.route) {
            DownloadProgressScreen(
                viewModel = receiveViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onDownloadSuccess = {
                    navController.navigate(Screen.DownloadComplete.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.DownloadComplete.route) {
            DownloadCompleteScreen(
                viewModel = receiveViewModel,
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                currentUser = currentUser,
                onBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate(Screen.Auth.route) }
            )
        }

        composable(Screen.Auth.route) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
