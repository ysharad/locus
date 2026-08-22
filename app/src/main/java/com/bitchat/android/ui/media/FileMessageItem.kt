package com.bitchat.android.ui.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.features.file.FileUtils
import com.bitchat.android.model.BitchatFilePacket

/**
 * Modern chat-style file message display
 */
@Composable
fun FileMessageItem(
    packet: BitchatFilePacket,
    onFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth(0.8f)
            .clickable { showDialog = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File icon
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = stringResource(com.bitchat.android.R.string.cd_file),
                tint = getFileIconColor(packet.fileName),
                modifier = Modifier.size(32.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // File name
                    Text(
                        text = packet.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                // File details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = FileUtils.formatFileSize(packet.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // File type indicator
                    FileTypeBadge(mimeType = packet.mimeType)
                }
            }
        }
    }

    // File viewer dialog
    if (showDialog) {
        FileViewerDialog(
            packet = packet,
            onDismiss = { showDialog = false },
            onSaveToDevice = { content, fileName ->
                // In a real implementation, this would save to Downloads
                // For now, just log that file was "saved"
                android.util.Log.d("FileSharing", "Would save file: $fileName")
            }
        )
    }
}

/**
 * Small badge showing file type
 */
@Composable
private fun FileTypeBadge(mimeType: String) {
    // A file's type is a fact, not a rainbow: mono label, one quiet colour for all.
    val text = when {
        mimeType.startsWith("application/pdf") -> "PDF"
        mimeType.startsWith("text/") -> "TXT"
        mimeType.startsWith("image/") -> "IMG"
        mimeType.startsWith("audio/") -> "AUD"
        mimeType.startsWith("video/") -> "VID"
        mimeType.contains("document") -> "DOC"
        mimeType.contains("zip") || mimeType.contains("rar") -> "ZIP"
        else -> "FILE"
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    )
}

/**
 * Get appropriate icon color based on file extension
 */
private fun getFileIconColor(fileName: String): Color {
    // One accent for every attachment — copper, the app's single accent.
    return Color(0xFFCF7A45)
}
