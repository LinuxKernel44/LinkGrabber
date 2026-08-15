package com.linkgrabber.app.grabber

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

private const val TAG = "LinkGrabberExtension"
private const val EXTENSION_LOCATION = "resource://android/assets/extensions/linkgrabber/"
private const val EXTENSION_ID = "linkgrabber-core@linkgrabber.app"
private const val NATIVE_APP = "browser"

object LinkGrabberExtension {

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ extension ->
                if (extension != null) {
                    extension.setMessageDelegate(messageDelegate, NATIVE_APP)
                    Log.i(TAG, "Link Grabber Core extension installed")
                }
            }, { throwable ->
                Log.e(TAG, "Failed to install Link Grabber Core extension", throwable)
            })
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            val json = message as? JSONObject ?: return null
            if (json.optString("type") == "grabbed_link") handleGrabbedLink(json)
            return null
        }
    }

    private fun handleGrabbedLink(json: JSONObject) {
        val tabId = json.optInt("tabId", -1)
        if (tabId < 0) return

        val link = json.optJSONObject("link") ?: return
        val grabbedLink = GrabbedLink(
            url = link.optString("url"),
            filename = link.optString("filename"),
            mimeType = link.nullableString("mimeType"),
            size = if (link.has("size") && !link.isNull("size")) link.optLong("size") else null,
            source = link.optString("source", "network"),
            pageUrl = json.nullableString("pageUrl"),
            pageTitle = json.nullableString("pageTitle")
        )
        if (grabbedLink.url.isBlank()) return

        LinkGrabberRepository.addLink(tabId, grabbedLink)
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).ifBlank { null }
    }
}
