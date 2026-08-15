package com.linkgrabber.app.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

class Tab(val session: GeckoSession, initialUrl: String) {
    val id: String = UUID.randomUUID().toString()
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var canGoBack by mutableStateOf(false)
}
