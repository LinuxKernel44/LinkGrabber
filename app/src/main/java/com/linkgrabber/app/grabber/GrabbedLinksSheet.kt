package com.linkgrabber.app.grabber

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrabbedLinksSheet(links: List<GrabbedLink>, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (links.isEmpty()) "No download links detected yet" else "Detected download links",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                )
                if (links.isNotEmpty()) {
                    IconButton(onClick = { LinkGrabberRepository.clearAll() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear detected links")
                    }
                }
            }
            LazyColumn {
                items(links.reversed()) { link ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = link.filename, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(text = describeLink(link), maxLines = 1)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(link.url)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link")
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, link.url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share link"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share link")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun describeLink(link: GrabbedLink): String {
    val size = link.size?.let { formatBytes(it) }
    return listOfNotNull(link.mimeType, size).joinToString(" · ").ifBlank { link.url }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
