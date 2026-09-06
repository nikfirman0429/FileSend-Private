package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.DiagnosticLogEntry
import com.example.data.remote.DiagnosticLogger
import com.example.data.remote.LogCategory
import com.example.ui.theme.OnPrimaryPurpleContainer
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleContainer
import com.example.ui.theme.SecondaryLilacContainer
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekGreenContainer
import com.example.ui.theme.SleekLightOutline
import com.example.ui.theme.SleekRed
import com.example.ui.theme.SleekRedContainer

enum class DiagnosticFilter {
    ALL,
    ERRORS,
    SEARCH,
    UPLOAD
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkDiagnosticSheet(
    onDismissRequest: () -> Unit,
    initialFilter: DiagnosticFilter = DiagnosticFilter.ALL
) {
    val context = LocalContext.current
    val logs by DiagnosticLogger.logs.collectAsState()
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    var showHelpSection by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            DiagnosticFilter.ALL -> logs
            DiagnosticFilter.ERRORS -> logs.filter { it.isError || (it.statusCode != null && it.statusCode >= 400) }
            DiagnosticFilter.SEARCH -> logs.filter {
                it.tag.contains("SEARCH", ignoreCase = true) ||
                        it.url?.contains("/api/files/", ignoreCase = true) == true ||
                        it.tag.contains("CLOUD_RELAY", ignoreCase = true)
            }
            DiagnosticFilter.UPLOAD -> logs.filter {
                it.tag.contains("UPLOAD", ignoreCase = true) ||
                        it.url?.contains("/api/upload", ignoreCase = true) == true ||
                        it.tag.contains("CLOUD_STORAGE", ignoreCase = true)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .testTag("network_diagnostic_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryPurpleContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Http,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Retrofit Network Diagnostics",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${logs.size} transactions recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showHelpSection = !showHelpSection },
                        modifier = Modifier.testTag("diag_help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Troubleshooting Help",
                            tint = if (showHelpSection) PrimaryPurple else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("diag_close_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Copy all, Clear, Toggle Troubleshooting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val text = DiagnosticLogger.exportDiagnosticsText()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("FileSend Network Diagnostics", text))
                        Toast.makeText(context, "All diagnostic logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("copy_all_logs_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All Logs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        DiagnosticLogger.clearLogs()
                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("clear_logs_button")
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear", fontSize = 13.sp)
                }
            }

            // Troubleshooting help card
            AnimatedVisibility(visible = showHelpSection) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryLilacContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Why a 6-digit code lookup might fail:",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryPurple
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• HTTP 404: The file code hasn't finished uploading yet, has expired, or was typed with an error.\n" +
                                    "• HTTP 502/Connection Refused: If using a custom server URL in Settings, verify the server is running and reachable.\n" +
                                    "• Built-in Relay: Automatic cloud synchronization queries global relays. Check raw response below to verify topic message contents.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == DiagnosticFilter.ALL,
                    onClick = { selectedFilter = DiagnosticFilter.ALL },
                    label = { Text("All (${logs.size})") },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryPurpleContainer,
                        selectedLabelColor = PrimaryPurple
                    )
                )
                val errorCount = logs.count { it.isError || (it.statusCode != null && it.statusCode >= 400) }
                FilterChip(
                    selected = selectedFilter == DiagnosticFilter.ERRORS,
                    onClick = { selectedFilter = DiagnosticFilter.ERRORS },
                    label = { Text("Errors / 404 ($errorCount)") },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SleekRedContainer,
                        selectedLabelColor = SleekRed
                    )
                )
                FilterChip(
                    selected = selectedFilter == DiagnosticFilter.SEARCH,
                    onClick = { selectedFilter = DiagnosticFilter.SEARCH },
                    label = { Text("Code Search") },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SecondaryLilacContainer,
                        selectedLabelColor = PrimaryPurple
                    )
                )
                FilterChip(
                    selected = selectedFilter == DiagnosticFilter.UPLOAD,
                    onClick = { selectedFilter = DiagnosticFilter.UPLOAD },
                    label = { Text("Uploads") },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SecondaryLilacContainer,
                        selectedLabelColor = PrimaryPurple
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Logs List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Http,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No diagnostic events match current filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val isExpanded = expandedLogId == log.id
                        DiagnosticLogCard(
                            log = log,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedLogId = if (isExpanded) null else log.id
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogCard(
    log: DiagnosticLogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current

    val containerColor = when {
        log.statusCode != null && log.statusCode in 200..299 -> MaterialTheme.colorScheme.surface
        log.statusCode == 404 -> SleekRedContainer.copy(alpha = 0.35f)
        log.isError -> SleekRedContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }

    val badgeColor = when {
        log.statusCode != null && log.statusCode in 200..299 -> SleekGreen
        log.statusCode == 404 -> SleekRed
        log.isError -> SleekRed
        else -> PrimaryPurple
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("diag_card_${log.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Tag, Timestamp, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = log.tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = log.formattedTime,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = log.statusBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // URL or Message
            if (log.url != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (log.method != null) {
                        Text(
                            text = log.method,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = log.url,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) 10 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (log.isError) SleekRed else MaterialTheme.colorScheme.onSurface
                )
            }

            // Duration & Preview Snippet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (log.durationMs != null) {
                    Text(
                        text = "Latency: ${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isExpanded) "Hide details" else "View raw response",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryPurple
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expanded Details View
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Request Payload (if present)
                    if (!log.requestBody.isNullOrBlank()) {
                        Text(
                            text = "REQUEST PAYLOAD:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = log.requestBody,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Exact Response Body Header with Copy Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXACT BACKEND RESPONSE BODY:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!log.responseBody.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Response Body", log.responseBody))
                                    Toast.makeText(context, "Response body copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Exact Response Body Content Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E2E))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val bodyText = log.responseBody?.ifBlank { "[Empty Response Body]" } ?: "[No Response Body]"
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = Color(0xFFA6E3A1)
                        )
                    }

                    // Full Diagnostic Details
                    if (!log.details.isNullOrBlank() && log.details != log.responseBody) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "DIAGNOSTIC DETAILS:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
