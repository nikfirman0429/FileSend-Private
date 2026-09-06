package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdBanner
import com.example.ui.components.FileSendTopAppBar
import com.example.ui.components.InfoRow
import com.example.ui.components.NetworkDiagnosticSheet
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.OnPrimaryPurpleContainer
import com.example.ui.theme.PrimaryPurpleContainer
import com.example.ui.theme.SecondaryLilacContainer
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekRed
import com.example.ui.viewmodels.ConnectionTestStatus
import com.example.ui.viewmodels.SettingsViewModel
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    currentUser: FirebaseUser? = null,
    onBack: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val serverUrlInput by viewModel.serverUrlInput.collectAsState()
    val chunkSizeBytes by viewModel.chunkSizeBytes.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()

    Scaffold(
        topBar = {
            FileSendTopAppBar(
                title = "Settings",
                onBack = onBack
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
            // Account & Security Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_settings_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Account",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACCOUNT & AUTHENTICATION",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (currentUser != null) {
                        val isGoogle = currentUser.providerData.any { it.providerId == "google.com" }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val initial = (currentUser.displayName?.firstOrNull() ?: currentUser.email?.firstOrNull() ?: 'U').uppercaseChar().toString()
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryPurpleContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimaryPurpleContainer,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "FileSend User",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = currentUser.email ?: "No email",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isGoogle) "Google Account" else "Email & Password Account",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = PrimaryPurple
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manage_account_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SecondaryLilacContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Manage Account / Sign Out", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            text = "Authentication required to use FileSend. Please sign in or register your account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_sign_in_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Text("Sign In or Register", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Backend Server Configuration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_settings_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Server",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BACKEND SERVER URL",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Specify your deployed FileSend backend host, custom server domain, or use the built-in functional private relay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isBuiltIn = serverUrlInput.trim().contains("filesend-private.local") || serverUrlInput.trim().contains("filesend.app")
                        FilterChip(
                            selected = isBuiltIn,
                            onClick = {
                                viewModel.updateServerUrl("https://filesend-private.local")
                                viewModel.saveServerUrl()
                            },
                            label = { Text("Global Cloud Relay (Active)", fontWeight = if (isBuiltIn) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isBuiltIn) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SecondaryLilacContainer,
                                selectedLabelColor = PrimaryPurple,
                                selectedLeadingIconColor = PrimaryPurple
                            )
                        )

                        val isLocalhost = serverUrlInput.trim().contains("10.0.2.2") || serverUrlInput.trim().contains("localhost")
                        FilterChip(
                            selected = isLocalhost,
                            onClick = {
                                viewModel.updateServerUrl("http://10.0.2.2:8080")
                                viewModel.saveServerUrl()
                            },
                            label = { Text("Localhost (10.0.2.2:8080)") },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SecondaryLilacContainer,
                                selectedLabelColor = PrimaryPurple
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { viewModel.updateServerUrl(it) },
                        placeholder = { Text("https://filesend-private.local") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveServerUrl() },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("save_server_url_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save URL", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.testConnection() },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                                .testTag("test_connection_button")
                        ) {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Health", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Test Status Indicator
                    when (val status = testStatus) {
                        is ConnectionTestStatus.Testing -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryPurple
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connecting to server...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is ConnectionTestStatus.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = SleekGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = SleekGreen
                                )
                            }
                        }
                        is ConnectionTestStatus.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = SleekRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SleekRed
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Chunk Size Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Chunk Size",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UPLOAD CHUNK SIZE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Chunk size for slicing large files during streaming uploads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val chunkOptions = listOf(
                        2 * 1024 * 1024L to "2 MB (Slow)",
                        4 * 1024 * 1024L to "4 MB (Default)",
                        8 * 1024 * 1024L to "8 MB (Fast)"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunkOptions.forEach { (size, label) ->
                            val selected = chunkSizeBytes == size
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setChunkSizeBytes(size) },
                                label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SecondaryLilacContainer,
                                    selectedLabelColor = PrimaryPurple,
                                    selectedLeadingIconColor = PrimaryPurple
                                )
                            )
                        }
                    }
                }
            }

            // Retrofit & Network Diagnostics Card
            var showDiagnosticSheet by remember { mutableStateOf(false) }
            if (showDiagnosticSheet) {
                NetworkDiagnosticSheet(
                    onDismissRequest = { showDiagnosticSheet = false }
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_settings_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "Diagnostics",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NETWORK & API DIAGNOSTICS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Inspect exact HTTP status codes, raw JSON response bodies, and latency logs for Retrofit requests and 6-digit code lookups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showDiagnosticSheet = true },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_diagnostics_button")
                    ) {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN NETWORK DIAGNOSTIC LOGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Security & Privacy Specs Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security",
                            tint = SleekGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SECURITY & INTEGRITY",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    InfoRow("Private Mode", "ACTIVE (PRIVATE_MODE=true)")
                    InfoRow("Search Indexing", "DISABLED (X-Robots-Tag)")
                    InfoRow("Checksum Algorithm", "SHA-256 (Streaming)")
                    InfoRow("Data Serialization", "Zero-Corruption Binary Stream")
                    InfoRow("Memory Protection", "Constant 64KB RAM footprint")
                }
            }

            // App About
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ABOUT FILESEND PRIVATE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    InfoRow("App Version", "1.0.0 (Production Build)")
                    InfoRow("Database", "Room (Local SQLite)")
                    InfoRow("Transfer Engine", "Chunked Resumable HTTP/2")
                }
            }

            // Sponsored Banner Ad
            AdBanner(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
