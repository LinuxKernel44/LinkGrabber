// Link Grabber Core - content script
// Scans the visible DOM for links that look like downloads, including ones
// hiding behind a "Download" labelled button rather than a plain <a href>.
// Real, authoritative detection happens in the background script via network
// interception - this is a best-effort complement for links that never
// trigger a network request until clicked (e.g. javascript: handlers).

(function () {
  const DOWNLOAD_EXTENSIONS = [
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso",
    "exe", "msi", "dmg", "pkg", "deb", "rpm", "apk", "appimage",
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "mp3", "flac", "wav", "aac", "ogg",
    "mp4", "mkv", "avi", "mov", "webm",
    "torrent"
  ];

  const DOWNLOAD_WORD = /\bdownload\b/i;
  const URL_IN_TEXT = /https?:\/\/[^\s"'()<>]+/i;

  function hasDownloadExtension(url) {
    try {
      const pathname = new URL(url, location.href).pathname;
      const match = pathname.match(/\.([a-zA-Z0-9]{1,6})$/);
      return match ? DOWNLOAD_EXTENSIONS.includes(match[1].toLowerCase()) : false;
    } catch (e) {
      return false;
    }
  }

  function resolveUrl(url) {
    try {
      return new URL(url, location.href).href;
    } catch (e) {
      return null;
    }
  }

  function filenameFromUrl(url) {
    try {
      const parts = new URL(url).pathname.split("/").filter(Boolean);
      return parts.length ? decodeURIComponent(parts[parts.length - 1]) : url;
    } catch (e) {
      return url;
    }
  }

  function fromOnclick(el) {
    const onclick = el.getAttribute("onclick");
    if (!onclick) return null;
    const match = onclick.match(URL_IN_TEXT);
    return match ? match[0] : null;
  }

  function collectCandidates() {
    const found = new Map();

    document.querySelectorAll("a[href]").forEach((a) => {
      const href = a.getAttribute("href");
      if (!href || href.startsWith("javascript:") || href.startsWith("#")) return;
      const resolved = resolveUrl(href);
      if (!resolved) return;

      const looksLikeDownload =
        hasDownloadExtension(resolved) ||
        a.hasAttribute("download") ||
        DOWNLOAD_WORD.test(a.textContent || "") ||
        DOWNLOAD_WORD.test(a.getAttribute("aria-label") || "");

      if (looksLikeDownload) {
        found.set(resolved, filenameFromUrl(resolved));
      }
    });

    document.querySelectorAll("button, [role=button], span, div").forEach((el) => {
      const text = (el.textContent || "").trim();
      if (text.length > 60 || !DOWNLOAD_WORD.test(text)) return;

      const dataUrl = el.getAttribute("data-href") || el.getAttribute("data-url");
      const onclickUrl = fromOnclick(el);
      const candidate = dataUrl || onclickUrl;
      if (!candidate) return;

      const resolved = resolveUrl(candidate);
      if (resolved) found.set(resolved, filenameFromUrl(resolved));
    });

    return Array.from(found.entries()).map(([url, filename]) => ({ url, filename }));
  }

  function report() {
    const links = collectCandidates();
    if (!links.length) return;
    browser.runtime.sendMessage({
      type: "dom_candidate_links",
      pageUrl: location.href,
      pageTitle: document.title,
      links
    }).catch(() => {});
  }

  report();

  let debounceHandle = null;
  const observer = new MutationObserver(() => {
    clearTimeout(debounceHandle);
    debounceHandle = setTimeout(report, 800);
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
})();
