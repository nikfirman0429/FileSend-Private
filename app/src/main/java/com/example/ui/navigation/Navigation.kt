package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object SendFile : Screen("send_file")
    object UploadProgress : Screen("upload_progress")
    object UploadComplete : Screen("upload_complete")
    object ReceiveFile : Screen("receive_file")
    object FileDetails : Screen("file_details")
    object DownloadProgress : Screen("download_progress")
    object DownloadComplete : Screen("download_complete")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Auth : Screen("auth")
}
