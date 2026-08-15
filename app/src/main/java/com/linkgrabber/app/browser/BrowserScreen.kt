package com.linkgrabber.app.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkgrabber.app.data.AppDatabase
import com.linkgrabber.app.data.BookmarkEntry
import com.linkgrabber.app.grabber.GrabbedLinksSheet
import com.linkgrabber.app.grabber.LinkGrabberRepository
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    tabManager: TabManager,
    onOpenExtensions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val tab = tabManager.currentTab ?: return
    var urlText by remember(tab.id) { mutableStateOf(tab.url) }
    var showLinksSheet by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }

    LaunchedEffect(tab.url) {
        urlText = tab.url
        isBookmarked = database.bookmarkDao().findByUrl(tab.url) != null
    }

    val linksByTab by LinkGrabberRepository.linksByTab.collectAsState()
    val grabbedLinks = remember(linksByTab) { linksByTab.values.flatten() }

    // The system back button/gesture navigates the page back, matching how
    // mobile browsers behave - keeps the toolbar free of a dedicated Back
    // button so the address field has room to breathe.
    BackHandler(enabled = tab.canGoBack) {
        tab.session.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { tab.session.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    tab.session.loadUri(normalizeUrl(urlText))
                })
            )
            IconButton(onClick = {
                scope.launch {
                    val existing = database.bookmarkDao().findByUrl(tab.url)
                    if (existing != null) {
                        database.bookmarkDao().delete(existing)
                        isBookmarked = false
                    } else {
                        database.bookmarkDao().insert(BookmarkEntry(url = tab.url, title = tab.title))
                        isBookmarked = true
                    }
                }
            }) {
                Icon(
                    if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Bookmark this page"
                )
            }
            IconButton(onClick = { showLinksSheet = true }) {
                BadgedBox(badge = {
                    if (grabbedLinks.isNotEmpty()) {
                        Badge { Text(grabbedLinks.size.toString()) }
                    }
                }) {
                    Icon(Icons.Filled.FileDownload, contentDescription = "Detected download links")
                }
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Forward") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    onClick = { showMenu = false; tab.session.goForward() }
                )
                DropdownMenuItem(
                    text = { Text("New tab") },
                    onClick = { showMenu = false; tabManager.newTab() }
                )
                DropdownMenuItem(
                    text = { Text("Tabs (${tabManager.tabs.size})") },
                    leadingIcon = { Icon(Icons.Filled.Tab, contentDescription = null) },
                    onClick = { showMenu = false; showTabSwitcher = true }
                )
                DropdownMenuItem(
                    text = { Text("Bookmarks") },
                    leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                    onClick = { showMenu = false; onOpenBookmarks() }
                )
                DropdownMenuItem(
                    text = { Text("History") },
                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                    onClick = { showMenu = false; onOpenHistory() }
                )
                DropdownMenuItem(
                    text = { Text("Extensions") },
                    leadingIcon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                    onClick = { showMenu = false; onOpenExtensions() }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = { showMenu = false; onOpenSettings() }
                )
            }
        }

        if (tab.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { tab.progress / 100f }
            )
        }

        TabContent(tabManager)
    }

    if (showLinksSheet) {
        GrabbedLinksSheet(links = grabbedLinks, onDismiss = { showLinksSheet = false })
    }
    if (showTabSwitcher) {
        TabSwitcherSheet(tabManager = tabManager, onDismiss = { showTabSwitcher = false })
    }
}

/**
 * A single GeckoView instance is reused across tabs (swapping which
 * GeckoSession it displays) so switching tabs doesn't reload the page or
 * recreate the native view - each tab keeps its own live session underneath.
 */
@Composable
private fun TabContent(tabManager: TabManager) {
    val tab = tabManager.currentTab ?: return
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> GeckoView(ctx) },
        update = { view -> view.setSession(tab.session) }
    )
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
        "https://$trimmed"
    } else {
        "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
    }
}
