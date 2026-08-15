package com.linkgrabber.app.grabber

data class GrabbedLink(
    val url: String,
    val filename: String,
    val mimeType: String?,
    val size: Long?,
    val source: String,
    val pageUrl: String?,
    val pageTitle: String?,
    val timestamp: Long = System.currentTimeMillis()
)
