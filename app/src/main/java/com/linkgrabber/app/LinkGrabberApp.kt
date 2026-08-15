package com.linkgrabber.app

import android.app.Application
import com.linkgrabber.app.extensions.ExtensionManager
import com.linkgrabber.app.grabber.LinkGrabberExtension
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class LinkGrabberApp : Application() {

    val geckoRuntime: GeckoRuntime by lazy {
        GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(true)
                .consoleOutput(true)
                .build()
        ).also { runtime ->
            LinkGrabberExtension.install(runtime)
            ExtensionManager.init(runtime)
        }
    }
}
