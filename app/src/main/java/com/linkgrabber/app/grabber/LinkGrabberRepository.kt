package com.linkgrabber.app.grabber

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds links detected by the bundled WebExtension, keyed by the app's own
 * tab id so each browser tab gets its own grabbed-links list.
 *
 * The extension only knows Gecko's internal tab id; [tabResolver] (installed
 * by TabManager) maps that id (plus the page URL) to an app tab id.
 */
object LinkGrabberRepository {

    private val _linksByTab = MutableStateFlow<Map<String, List<GrabbedLink>>>(emptyMap())
    val linksByTab: StateFlow<Map<String, List<GrabbedLink>>> = _linksByTab

    @Volatile
    var tabResolver: ((geckoTabId: Int, pageUrl: String?) -> String?)? = null

    fun addLink(geckoTabId: Int, link: GrabbedLink) {
        val tabId = tabResolver?.invoke(geckoTabId, link.pageUrl) ?: return
        _linksByTab.update { current ->
            val existing = current[tabId].orEmpty()
            if (existing.any { it.url == link.url }) return@update current
            current + (tabId to (existing + link))
        }
    }

    fun clearTab(tabId: String) {
        _linksByTab.update { current -> current - tabId }
    }

    fun clearAll() {
        _linksByTab.value = emptyMap()
    }
}
