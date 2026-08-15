package com.linkgrabber.app.extensions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.WebExtension
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by ExtensionManager.installed.collectAsState()
    val installingKeys by ExtensionManager.installingKeys.collectAsState()
    val lastError by ExtensionManager.lastError.collectAsState()
    val pendingPrompt by ExtensionManager.pendingPrompt.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = "sideload-${System.currentTimeMillis()}.xpi"
        val destination = File(context.filesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        ExtensionManager.install(Uri.fromFile(destination).toString(), key = fileName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Text(" Install from file (.xpi)", modifier = Modifier.padding(start = 8.dp))
                }
                lastError?.let { (_, message) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(text = message, modifier = Modifier.weight(1f))
                        IconButton(onClick = { ExtensionManager.clearError() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            if (installed.isNotEmpty()) {
                item {
                    Text("Installed", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(installed, key = { it.id }) { extension ->
                    InstalledExtensionRow(extension)
                    HorizontalDivider()
                }
            }

            item {
                Text(
                    "Add extensions",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            items(ExtensionCatalog.extensions, key = { it.xpiUrl }) { catalogExtension ->
                val alreadyInstalled = installed.any { it.metaData?.name == catalogExtension.name }
                val isInstalling = installingKeys.contains(catalogExtension.xpiUrl)
                CatalogExtensionRow(
                    extension = catalogExtension,
                    isInstalled = alreadyInstalled,
                    isInstalling = isInstalling,
                    onInstall = { ExtensionManager.install(catalogExtension.xpiUrl, catalogExtension.xpiUrl) }
                )
                HorizontalDivider()
            }
        }
    }

    pendingPrompt?.let { prompt ->
        InstallPermissionDialog(prompt)
    }
}

@Composable
private fun InstalledExtensionRow(extension: WebExtension) {
    val meta = extension.metaData
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = meta?.name ?: extension.id, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            meta?.description?.let { Text(text = it, maxLines = 2) }
        }
        Switch(
            checked = meta?.enabled ?: true,
            onCheckedChange = { checked -> ExtensionManager.setEnabled(extension, checked) }
        )
        IconButton(onClick = { ExtensionManager.uninstall(extension) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Uninstall")
        }
    }
}

@Composable
private fun CatalogExtensionRow(
    extension: CatalogExtension,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = extension.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(text = extension.tagline)
        }
        when {
            isInstalling -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            isInstalled -> Text("Installed")
            else -> Button(onClick = onInstall) { Text("Install") }
        }
    }
}

@Composable
private fun InstallPermissionDialog(prompt: PendingInstallPrompt) {
    val meta = prompt.extension.metaData
    AlertDialog(
        onDismissRequest = { prompt.respond(false) },
        title = { Text("Install ${meta?.name ?: prompt.extension.id}?") },
        text = {
            Column {
                Text("This extension will be able to:")
                (prompt.origins + prompt.permissions).distinct().forEach { permission ->
                    Text("• $permission")
                }
            }
        },
        confirmButton = {
            Button(onClick = { prompt.respond(true) }) { Text("Install") }
        },
        dismissButton = {
            OutlinedButton(onClick = { prompt.respond(false) }) { Text("Cancel") }
        }
    )
}
