package com.linkgrabber.app.browser

import android.net.Uri
import android.util.Base64
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
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebRequestError

const val DEFAULT_URL = "https://www.mozilla.org"

/**
 * Fake internal scheme used by the "continue anyway" link on the Safe
 * Browsing error page - lets a plain HTML <a href> trigger a real reload
 * with LOAD_FLAGS_BYPASS_CLASSIFIER, which a normal link can't request.
 */
private const val BYPASS_CLASSIFIER_SCHEME = "linkgrabber-bypass"

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

            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val target = request.uri.removeBypassClassifierPrefix() ?: return null
                s.load(
                    GeckoSession.Loader()
                        .uri(target)
                        .flags(GeckoSession.LOAD_FLAGS_BYPASS_CLASSIFIER)
                )
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            // Without this, a failed load (blocked by Safe Browsing, a bad
            // certificate, a network error, ...) leaves the session showing
            // nothing - GeckoView has no built-in fallback content, so the
            // page just looks blank with no explanation.
            override fun onLoadError(
                s: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String> {
                return GeckoResult.fromValue(buildErrorPageUri(uri, error))
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

private fun String.removeBypassClassifierPrefix(): String? {
    val prefix = "$BYPASS_CLASSIFIER_SCHEME:"
    if (!startsWith(prefix)) return null
    return Uri.decode(removePrefix(prefix))
}

private fun describeError(error: WebRequestError): Triple<String, String, Boolean> {
    return when (error.category) {
        WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> Triple(
            "Site potentiellement dangereux",
            "Ce site a été bloqué car il est signalé comme site de hameçonnage, " +
                "de logiciel malveillant ou de logiciel indésirable.",
            true
        )
        WebRequestError.ERROR_CATEGORY_SECURITY -> Triple(
            "Connexion non sécurisée",
            "Le certificat de sécurité de ce site n'est pas valide. La connexion a été bloquée.",
            false
        )
        WebRequestError.ERROR_CATEGORY_NETWORK -> Triple(
            "Impossible d'accéder à ce site",
            "Vérifiez votre connexion internet ou réessayez plus tard.",
            false
        )
        WebRequestError.ERROR_CATEGORY_URI -> Triple(
            "Adresse invalide",
            "Cette adresse ne peut pas être ouverte.",
            false
        )
        WebRequestError.ERROR_CATEGORY_CONTENT -> Triple(
            "Contenu bloqué",
            "Le contenu de cette page est non sécurisé ou corrompu et a été bloqué.",
            false
        )
        else -> Triple(
            "Impossible d'afficher la page",
            "Une erreur est survenue lors du chargement de cette page.",
            false
        )
    }
}

private fun buildErrorPageUri(uri: String?, error: WebRequestError): String {
    val (title, message, canBypass) = describeError(error)
    val retryLink = uri?.let { "<a class=\"btn\" href=\"${it.htmlEscape()}\">Réessayer</a>" }.orEmpty()
    val bypassLink = if (canBypass && uri != null) {
        val target = "$BYPASS_CLASSIFIER_SCHEME:${Uri.encode(uri)}"
        "<a class=\"btn btn-danger\" href=\"${target.htmlEscape()}\">Continuer quand même (déconseillé)</a>"
    } else {
        ""
    }
    val html = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { font-family: sans-serif; background:#fff; color:#1c1c1e; padding:32px 24px; }
            h1 { font-size:20px; margin-bottom:8px; }
            p { color:#555; line-height:1.4; }
            .url { word-break:break-all; color:#888; font-size:13px; margin:16px 0; }
            .btn { display:inline-block; margin:4px 8px 4px 0; padding:10px 20px;
                   background:#0060df; color:#fff; text-decoration:none; border-radius:6px; }
            .btn-danger { background:#c50042; }
        </style></head>
        <body>
            <h1>${title.htmlEscape()}</h1>
            <p>${message.htmlEscape()}</p>
            <div class="url">${(uri ?: "").htmlEscape()}</div>
            $retryLink
            $bypassLink
        </body></html>
    """.trimIndent()
    val encoded = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return "data:text/html;charset=utf-8;base64,$encoded"
}

private fun String.htmlEscape(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
