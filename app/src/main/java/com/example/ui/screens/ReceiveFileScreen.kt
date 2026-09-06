package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.DiagnosticLogger
import com.example.engine.FileUtils
import com.example.ui.components.AdBanner
import com.example.ui.components.DiagnosticFilter
import com.example.ui.components.FileIconBadge
import com.example.ui.components.FileSendTopAppBar
import com.example.ui.components.InfoRow
import com.example.ui.components.NetworkDiagnosticSheet
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleContainer
import com.example.ui.theme.SecondaryLilacContainer
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekRed
import com.example.ui.theme.SleekRedContainer
import com.example.ui.viewmodels.ReceiveViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiveFileScreen(
    viewModel: ReceiveViewModel,
    initialCode: String? = null,
    onBack: () -> Unit,
    onStartDownload: () -> Unit
) {
    val context = LocalContext.current
    val inputCode by viewModel.inputCode.collectAsState()
    val queriedMetadata by viewModel.queriedMetadata.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val diagnosticLogs by DiagnosticLogger.logs.collectAsState()

    var showDiagnosticSheet by remember { mutableStateOf(false) }
    var diagnosticFilter by remember { mutableStateOf(DiagnosticFilter.ALL) }

    LaunchedEffect(initialCode) {
        if (!initialCode.isNullOrBlank() && inputCode.isBlank()) {
            viewModel.setInputCode(initialCode)
            viewModel.findFile(initialCode)
        }
    }

    if (showDiagnosticSheet) {
        NetworkDiagnosticSheet(
            onDismissRequest = { showDiagnosticSheet = false },
            initialFilter = diagnosticFilter
        )
    }

    Scaffold(
        topBar = {
            FileSendTopAppBar(
                title = "Receive File",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            diagnosticFilter = DiagnosticFilter.SEARCH
                            showDiagnosticSheet = true
                        },
                        modifier = Modifier.testTag("top_bar_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Http,
                            contentDescription = "Network Diagnostics",
                            tint = PrimaryPurple
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Code Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receive_code_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "ENTER 6-CHARACTER CODE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = {
                                viewModel.setInputCode(it)
                            },
                            placeholder = { Text("e.g. X7K92P") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!item.isNullOrBlank()) {
                                            viewModel.setInputCode(item)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = PrimaryPurple
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receive_code_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.findFile() },
                        enabled = inputCode.isNotBlank() && !isSearching,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("lookup_file_button")
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Locating File...")
                        } else {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("FIND FILE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Error Display with Direct Diagnostic Inspection
            if (searchError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_error_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SleekRedContainer),
                    border = null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = SleekRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = searchError!!,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = SleekRed,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Inspect Exact Backend Response & Status Button
                        OutlinedButton(
                            onClick = {
                                diagnosticFilter = DiagnosticFilter.ERRORS
                                showDiagnosticSheet = true
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("view_diagnostics_error_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Http,
                                contentDescription = null,
                                tint = SleekRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View Backend Response & Diagnostic Log",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = SleekRed
                            )
                        }
                    }
                }
            }

            // Quick Diagnostic Bar
            Surface(
                onClick = {
                    diagnosticFilter = DiagnosticFilter.ALL
                    showDiagnosticSheet = true
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_diagnostics_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Http,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Retrofit Network Diagnostic Monitor",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val lastEntry = diagnosticLogs.firstOrNull()
                            Text(
                                text = lastEntry?.statusBadge ?: "Listening to API responses...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Inspect",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryPurple
                    )
                }
            }

            // Queried File Metadata Card
            if (queriedMetadata != null) {
                val metadata = queriedMetadata!!

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queried_file_card"),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FileIconBadge(
                                extension = metadata.extension,
                                size = 48
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = metadata.originalName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2
                                )
                                Text(
                                    text = FileUtils.formatFileSize(metadata.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PrimaryPurple
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        InfoRow("Code", metadata.code, isMonospace = true)
                        InfoRow("File Type", metadata.extension.uppercase().ifBlank { "FILE" })
                        InfoRow("MIME Type", metadata.mimeType)

                        if (metadata.expiresAt != null) {
                            val expiryDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(metadata.expiresAt))
                            InfoRow("Expires At", expiryDate)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Checksum Preview
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Expected SHA-256 Checksum",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Verified",
                                        tint = SleekGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = metadata.checksum,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.startDownload()
                                onStartDownload()
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_download_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DOWNLOAD FILE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // Sponsored Banner Ad
            AdBanner(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

