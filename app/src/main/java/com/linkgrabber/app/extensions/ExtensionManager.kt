package com.linkgrabber.app.extensions

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

private const val TAG = "ExtensionManager"

data class PendingInstallPrompt(
    val extension: WebExtension,
    val permissions: List<String>,
    val origins: List<String>,
    val respond: (granted: Boolean) -> Unit
)

/** Installs, lists and manages user-facing WebExtensions (as opposed to the built-in Link Grabber Core). */
object ExtensionManager {

    private lateinit var runtime: GeckoRuntime

    private val _installed = MutableStateFlow<List<WebExtension>>(emptyList())
    val installed: StateFlow<List<WebExtension>> = _installed

    private val _installingKeys = MutableStateFlow<Set<String>>(emptySet())
    val installingKeys: StateFlow<Set<String>> = _installingKeys

    private val _lastError = MutableStateFlow<Pair<String, String>?>(null)
    val lastError: StateFlow<Pair<String, String>?> = _lastError

    private val _pendingPrompt = MutableStateFlow<PendingInstallPrompt?>(null)
    val pendingPrompt: StateFlow<PendingInstallPrompt?> = _pendingPrompt

    fun init(runtime: GeckoRuntime) {
        this.runtime = runtime
        runtime.webExtensionController.setPromptDelegate(promptDelegate)
        refresh()
    }

    fun refresh() {
        runtime.webExtensionController.list().accept({ extensions ->
            _installed.value = extensions.orEmpty().filterNot { it.isBuiltIn }
        }, { throwable ->
            Log.e(TAG, "Failed to list extensions", throwable)
        })
    }

    fun install(uri: String, key: String) {
        _installingKeys.update { it + key }
        runtime.webExtensionController.install(uri).accept({ extension ->
            _installingKeys.update { it - key }
            refresh()
        }, { throwable ->
            _installingKeys.update { it - key }
            val message = describeError(throwable)
            _lastError.value = key to message
            Log.e(TAG, "Failed to install extension $key", throwable)
        })
    }

    fun uninstall(extension: WebExtension) {
        runtime.webExtensionController.uninstall(extension).accept({
            refresh()
        }, { throwable ->
            Log.e(TAG, "Failed to uninstall extension", throwable)
        })
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        val call = if (enabled) {
            runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.USER)
        }
        call.accept({ refresh() }, { throwable ->
            Log.e(TAG, "Failed to toggle extension", throwable)
        })
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun describeError(throwable: Throwable?): String {
        val installException = throwable as? WebExtension.InstallException
            ?: return throwable?.message ?: "Installation failed"
        return when (installException.code) {
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                "This extension isn't signed by Mozilla, so it can't be installed. " +
                    "Firefox-based browsers only allow signed extensions for security."
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                "Couldn't download the extension. Check your connection and try again."
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE,
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                "This file isn't a valid extension package."
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                "This extension isn't compatible with this browser."
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED,
            WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED ->
                "This extension has been blocklisted for security reasons."
            WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
                "Installation was canceled."
            else -> "Installation failed."
        }
    }

    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            _pendingPrompt.value = PendingInstallPrompt(
                extension = extension,
                permissions = permissions.toList(),
                origins = origins.toList(),
                respond = { granted ->
                    _pendingPrompt.value = null
                    result.complete(WebExtension.PermissionPromptResponse(granted, false, false))
                }
            )
            return result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<out String>,
            newOrigins: Array<out String>,
            newDataCollectionPermissions: Array<out String>
        ): GeckoResult<AllowOrDeny> {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
    }
}
