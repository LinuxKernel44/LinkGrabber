package com.linkgrabber.app.extensions

data class CatalogExtension(
    val name: String,
    val tagline: String,
    val xpiUrl: String
)

/**
 * A small, hand-picked set of popular Android-compatible extensions from
 * addons.mozilla.org. The "latest" download alias keeps these URLs valid
 * across extension updates instead of pinning a specific version file.
 */
object ExtensionCatalog {
    val extensions = listOf(
        CatalogExtension(
            name = "uBlock Origin",
            tagline = "Efficient, wide-spectrum content blocker",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        ),
        CatalogExtension(
            name = "Dark Reader",
            tagline = "Dark mode for every website",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi"
        ),
        CatalogExtension(
            name = "Privacy Badger",
            tagline = "Automatically blocks hidden trackers",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi"
        ),
        CatalogExtension(
            name = "ClearURLs",
            tagline = "Strips tracking parameters from links",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/clearurls/latest.xpi"
        ),
        CatalogExtension(
            name = "SponsorBlock",
            tagline = "Skips sponsor segments on YouTube",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/latest.xpi"
        ),
        CatalogExtension(
            name = "Tampermonkey",
            tagline = "Popular userscript manager",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/tampermonkey/latest.xpi"
        )
    )
}
