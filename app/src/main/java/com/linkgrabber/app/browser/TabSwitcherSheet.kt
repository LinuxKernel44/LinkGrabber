package com.linkgrabber.app.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(tabManager: TabManager, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tabs",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                )
                IconButton(onClick = {
                    tabManager.newTab()
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "New tab")
                }
            }
            LazyColumn {
                items(tabManager.tabs, key = { it.id }) { tab ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { tabManager.selectTab(tab.id); onDismiss() }
                        ) {
                            Text(
                                text = tab.title?.takeIf { it.isNotBlank() } ?: tab.url,
                                fontWeight = if (tab.id == tabManager.currentTabId) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            Text(text = tab.url, maxLines = 1)
                        }
                        IconButton(onClick = { tabManager.closeTab(tab.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close tab")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
