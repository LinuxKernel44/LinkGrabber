package com.linkgrabber.app.browser

import java.net.URLEncoder

private const val SEARCH_URL = "https://www.google.com/search?q="

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
private val KNOWN_SCHEMES = Regex("^(https?|about|file|view-source|data|javascript|resource):", RegexOption.IGNORE_CASE)
private val HOST_PORT = Regex("^[^\\s/?#:]+:\\d{1,5}([/?#].*)?$")
private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?([/?#].*)?$")
private val LOOPBACK_OR_LAN = Regex(
    "^(localhost|127\\.\\d+\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+)(:\\d{1,5})?([/?#].*)?$",
    RegexOption.IGNORE_CASE
)
// A dotted host with a plausible TLD, optionally followed by a path/query/fragment.
private val DOMAIN = Regex("^([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:\\d{1,5})?([/?#].*)?$")

/**
 * Turns whatever the user typed or pasted into a loadable URL: keeps explicit
 * schemes, upgrades bare domains to https (http for localhost/LAN addresses),
 * and falls back to a web search for anything else.
 */
fun normalizeUrl(input: String): String {
    val text = input.trim().replace(Regex("[\\r\\n\\t]"), "")
    if (text.isEmpty()) return DEFAULT_URL

    if (KNOWN_SCHEMES.containsMatchIn(text)) return text
    if (text.startsWith("//")) return "https:$text"

    if (!text.contains(' ')) {
        if (LOOPBACK_OR_LAN.matches(text)) return "http://$text"
        if (IPV4.matches(text) || DOMAIN.matches(text)) return "https://$text"
        // "host:port" (e.g. myserver:8080) - but not "foo:bar" style schemes
        if (HOST_PORT.matches(text)) return "http://$text"
        // Unknown explicit scheme (market:, intent:, ...) - pass through untouched.
        if (SCHEME.containsMatchIn(text) && text.contains("://")) return text
    }
    return SEARCH_URL + URLEncoder.encode(text, "UTF-8")
}

/** Short form shown when the field isn't being edited could be added later; for now the full URL is shown. */
