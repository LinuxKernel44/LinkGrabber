package com.linkgrabber.app.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linkgrabber.app.data.AppDatabase
import com.linkgrabber.app.data.HistoryEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val entries by database.historyDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { database.historyDao().clear() } }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("No history yet. History is off by default - turn it on in Settings if you want browsing history saved.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.id }) { entry: HistoryEntry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(entry.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title?.takeIf { it.isNotBlank() } ?: entry.url,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(text = entry.url, maxLines = 1)
                        }
                        IconButton(onClick = { scope.launch { database.historyDao().delete(entry.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
