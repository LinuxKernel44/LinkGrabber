// Link Grabber Core - background script
// Watches network responses for real, direct download links (network interception)
// and relays DOM-scan candidates reported by content_script.js.
// Everything found is forwarded to the host Android app over the WebExtension
// native-messaging bridge (browser.runtime.sendNativeMessage); the app never
// triggers a download itself, it only surfaces the links it finds (informational only).

const NATIVE_APP = "browser";

const DOWNLOAD_EXTENSIONS = [
  "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso",
  "exe", "msi", "dmg", "pkg", "deb", "rpm", "apk", "appimage",
  "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
  "mp3", "flac", "wav", "aac", "ogg",
  "mp4", "mkv", "avi", "mov", "webm",
  "torrent"
];

const DOWNLOAD_MIME_PREFIXES = [
  "application/octet-stream",
  "application/zip",
  "application/x-7z-compressed",
  "application/x-rar-compressed",
  "application/x-tar",
  "application/gzip",
  "application/x-msdownload",
  "application/x-msi",
  "application/vnd.android.package-archive",
  "application/x-apple-diskimage",
  "application/pdf",
  "application/x-bittorrent",
  "video/",
  "audio/"
];

const seen = new Set();
const linksByTab = new Map();

function extensionFromUrl(url) {
  try {
    const pathname = new URL(url).pathname;
    const match = pathname.match(/\.([a-zA-Z0-9]{1,6})$/);
    return match ? match[1].toLowerCase() : null;
  } catch (e) {
    return null;
  }
}

function filenameFromUrlOrHeader(url, contentDisposition) {
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
    if (match) {
      try {
        return decodeURIComponent(match[1]);
      } catch (e) {
        return match[1];
      }
    }
  }
  try {
    const pathname = new URL(url).pathname;
    const parts = pathname.split("/").filter(Boolean);
    return parts.length ? decodeURIComponent(parts[parts.length - 1]) : url;
  } catch (e) {
    return url;
  }
}

function isDownloadCandidate(url, headers) {
  let contentType = null;
  let contentDisposition = null;
  let contentLength = null;

  for (const h of headers || []) {
    const name = h.name.toLowerCase();
    if (name === "content-type") contentType = h.value;
    if (name === "content-disposition") contentDisposition = h.value;
    if (name === "content-length") contentLength = h.value;
  }

  const isAttachment = contentDisposition && /attachment/i.test(contentDisposition);
  const ext = extensionFromUrl(url);
  const extMatches = ext && DOWNLOAD_EXTENSIONS.includes(ext);
  const mimeMatches = contentType && DOWNLOAD_MIME_PREFIXES.some((p) => contentType.toLowerCase().startsWith(p));

  if (isAttachment || extMatches || mimeMatches) {
    return {
      url,
      filename: filenameFromUrlOrHeader(url, contentDisposition),
      mimeType: contentType || "application/octet-stream",
      size: contentLength ? parseInt(contentLength, 10) : null,
      source: "network"
    };
  }
  return null;
}

function reportLink(tabId, pageUrl, pageTitle, link) {
  const key = `${tabId}|${link.url}`;
  if (seen.has(key)) return;
  seen.add(key);

  if (!linksByTab.has(tabId)) linksByTab.set(tabId, []);
  linksByTab.get(tabId).push(link);

  browser.runtime.sendNativeMessage(NATIVE_APP, {
    type: "grabbed_link",
    tabId,
    pageUrl: pageUrl || null,
    pageTitle: pageTitle || null,
    link
  }).catch((e) => {
    console.error("[LinkGrabber] failed to report link", String(e));
  });
}

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    if (details.tabId < 0) return;
    if (!["main_frame", "sub_frame", "xmlhttprequest", "other"].includes(details.type)) return;

    const candidate = isDownloadCandidate(details.url, details.responseHeaders);
    if (!candidate) return;

    // A direct navigation to a downloadable file (main_frame) does not have a
    // separate "page" - use the request URL itself as context.
    browser.tabs.get(details.tabId).then(
      (tab) => reportLink(details.tabId, tab.url, tab.title, candidate),
      () => reportLink(details.tabId, details.originUrl || details.documentUrl || null, null, candidate)
    );
  },
  { urls: ["<all_urls>"] },
  ["responseHeaders"]
);

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || message.type !== "dom_candidate_links") return;
  const tabId = sender.tab ? sender.tab.id : -1;
  if (tabId < 0) return;

  for (const link of message.links) {
    reportLink(tabId, message.pageUrl, message.pageTitle, {
      url: link.url,
      filename: link.filename,
      mimeType: null,
      size: null,
      source: "dom"
    });
  }
});

// Deliberately not clearing on navigation: sites that hide a download behind
// a fake button often chain several redirects (and Gecko's own handling of
// an unrenderable response can itself look like a navigation event), so a
// link found earlier in that chain would be wiped right as it's found.
// Links accumulate for the tab's lifetime; the app exposes an explicit
// "clear" action to the user instead (handled entirely on the app side).
browser.tabs.onRemoved.addListener((tabId) => {
  linksByTab.delete(tabId);
  for (const key of Array.from(seen)) {
    if (key.startsWith(`${tabId}|`)) seen.delete(key);
  }
});
