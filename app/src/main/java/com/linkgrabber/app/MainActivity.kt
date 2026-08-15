package com.linkgrabber.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.linkgrabber.app.bookmarks.BookmarksScreen
import com.linkgrabber.app.browser.BrowserScreen
import com.linkgrabber.app.browser.DEFAULT_URL
import com.linkgrabber.app.browser.TabManager
import com.linkgrabber.app.data.AppDatabase
import com.linkgrabber.app.extensions.ExtensionsScreen
import com.linkgrabber.app.history.HistoryScreen
import com.linkgrabber.app.settings.SettingsRepository
import com.linkgrabber.app.settings.SettingsScreen
import com.linkgrabber.app.ui.theme.LinkGrabberTheme
import org.mozilla.geckoview.GeckoRuntime

private sealed class Screen {
    data object Browser : Screen()
    data object Extensions : Screen()
    data object Settings : Screen()
    data object History : Screen()
    data object Bookmarks : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val runtime: GeckoRuntime = (application as LinkGrabberApp).geckoRuntime
        val initialUrl = intent?.dataString

        setContent {
            LinkGrabberTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        val tabManager = remember {
                            TabManager(
                                runtime = runtime,
                                database = AppDatabase.get(context),
                                settings = SettingsRepository(context),
                                scope = scope
                            ).apply { newTab(initialUrl ?: DEFAULT_URL) }
                        }

                        var screen by remember { mutableStateOf<Screen>(Screen.Browser) }

                        when (screen) {
                            Screen.Browser -> BrowserScreen(
                                tabManager = tabManager,
                                onOpenExtensions = { screen = Screen.Extensions },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenHistory = { screen = Screen.History },
                                onOpenBookmarks = { screen = Screen.Bookmarks }
                            )
                            Screen.Extensions -> ExtensionsScreen(onBack = { screen = Screen.Browser })
                            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Browser })
                            Screen.History -> HistoryScreen(
                                onBack = { screen = Screen.Browser },
                                onOpenUrl = { url ->
                                    tabManager.currentTab?.session?.loadUri(url)
                                    screen = Screen.Browser
                                }
                            )
                            Screen.Bookmarks -> BookmarksScreen(
                                onBack = { screen = Screen.Browser },
                                onOpenUrl = { url ->
                                    tabManager.currentTab?.session?.loadUri(url)
                                    screen = Screen.Browser
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
