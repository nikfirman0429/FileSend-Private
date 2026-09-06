package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransferProgressState
import com.example.engine.FileUtils
import com.example.ui.components.AdBanner
import com.example.ui.components.CopyableCodeCard
import com.example.ui.components.FileIconBadge
import com.example.ui.components.FileSendTopAppBar
import com.example.ui.components.InfoRow
import com.example.ui.components.QrCodeModalDialog
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SecondaryLilacContainer
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekGreenContainer
import com.example.ui.viewmodels.SendViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UploadCompleteScreen(
    viewModel: SendViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsState()
    var showQrDialog by remember { mutableStateOf(false) }

    val successData = uploadState as? TransferProgressState.UploadSuccess

    if (successData == null) {
        onDone()
        return
    }

    if (showQrDialog) {
        QrCodeModalDialog(
            code = successData.code,
            shareUrl = successData.shareUrl,
            onDismiss = { showQrDialog = false }
        )
    }

    Scaffold(
        topBar = {
            FileSendTopAppBar(
                title = "Upload Complete",
                onBack = onDone
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
            // Success Header Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SleekGreenContainer.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = SleekGreen,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FILE READY TO SHARE",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Share this 6-character code or link with recipient",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Transfer Code Card
            CopyableCodeCard(code = successData.code)

            // Action Buttons: Share Link & QR Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Download file '${successData.fileName}' securely using FileSend Private:\nCode: ${successData.code}\nLink: ${successData.shareUrl}"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Transfer Code"))
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_link_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Link", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showQrDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("show_qr_button")
                ) {
                    Icon(imageVector = Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("QR Code", fontWeight = FontWeight.Bold)
                }
            }

            // File Information & Integrity Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FileIconBadge(
                            extension = FileUtils.getFileExtension(successData.fileName),
                            size = 48
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = successData.fileName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = FileUtils.formatFileSize(successData.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryPurple
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    InfoRow("File Size", FileUtils.formatFileSize(successData.size))

                    if (successData.expiresAt != null) {
                        val expiryDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(successData.expiresAt))
                        InfoRow("Expires At", expiryDate)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Checksum Row with verified badge
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
                                    text = "SHA-256 Checksum",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified",
                                    tint = SleekGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = successData.checksum,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Done Button
            Button(
                onClick = {
                    viewModel.resetState()
                    onDone()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryLilacContainer,
                    contentColor = PrimaryPurple
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("done_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "BACK TO HOME",
                    fontWeight = FontWeight.Bold
                )
            }

            // Sponsored Banner Ad
            AdBanner(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
