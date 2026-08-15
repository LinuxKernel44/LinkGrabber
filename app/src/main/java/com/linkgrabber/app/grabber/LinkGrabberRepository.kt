package com.linkgrabber.app.grabber

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds links detected by the bundled WebExtension, keyed by GeckoView's
 * internal tabId so each browser tab gets its own grabbed-links list.
 */
object LinkGrabberRepository {

    private val _linksByTab = MutableStateFlow<Map<Int, List<GrabbedLink>>>(emptyMap())
    val linksByTab: StateFlow<Map<Int, List<GrabbedLink>>> = _linksByTab

    fun addLink(tabId: Int, link: GrabbedLink) {
        _linksByTab.update { current ->
            val existing = current[tabId].orEmpty()
            if (existing.any { it.url == link.url }) return@update current
            current + (tabId to (existing + link))
        }
    }

    fun clearTab(tabId: Int) {
        _linksByTab.update { current -> current - tabId }
    }

    fun clearAll() {
        _linksByTab.value = emptyMap()
    }
}
