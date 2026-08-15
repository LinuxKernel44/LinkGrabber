package com.linkgrabber.app.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.linkgrabber.app.data.AppDatabase
import com.linkgrabber.app.data.HistoryEntry
import com.linkgrabber.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

const val DEFAULT_URL = "https://www.mozilla.org"

/**
 * Owns every open tab's GeckoSession and drives the tab list shown by the UI.
 * Also records history (only when enabled in settings - off by default).
 */
class TabManager(
    private val runtime: GeckoRuntime,
    private val database: AppDatabase,
    settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    val tabs = mutableStateListOf<Tab>()
    var currentTabId by mutableStateOf<String?>(null)
        private set

    val currentTab: Tab?
        get() = tabs.find { it.id == currentTabId }

    @Volatile private var historyEnabled = false

    init {
        scope.launch {
            settings.historyEnabled.collect { historyEnabled = it }
        }
    }

    fun newTab(url: String = DEFAULT_URL, select: Boolean = true): Tab {
        val session = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(false).build()
        )
        val tab = Tab(session, url)
        wireDelegates(tab)
        session.open(runtime)
        tabs.add(tab)
        if (select) currentTabId = tab.id
        session.loadUri(url)
        return tab
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]
        tabs.removeAt(index)
        tab.session.close()

        if (currentTabId == id) {
            currentTabId = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex.coerceAtLeast(0)))?.id
        }
        if (tabs.isEmpty()) newTab()
    }

    fun selectTab(id: String) {
        currentTabId = id
    }

    private fun wireDelegates(tab: Tab) {
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                s: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url != null) tab.url = url
            }

            override fun onCanGoBack(s: GeckoSession, canGoBackNow: Boolean) {
                tab.canGoBack = canGoBackNow
            }
        }

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                tab.title = title
            }
        }

        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                tab.isLoading = true
                tab.progress = 5
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                tab.isLoading = false
                tab.progress = 0
                if (success && historyEnabled && !tab.url.startsWith("about:")) {
                    val url = tab.url
                    val title = tab.title
                    scope.launch(Dispatchers.IO) {
                        database.historyDao().insert(HistoryEntry(url = url, title = title))
                    }
                }
            }

            override fun onProgressChange(s: GeckoSession, p: Int) {
                tab.progress = p
            }
        }
    }
}
