// Shorts Blocker — content script.
//
// Most hiding is done by content.css, which only applies while <html> has the
// `data-ysb-hide` attribute. This script:
//   1. toggles that attribute from the saved settings,
//   2. redirects Shorts URLs reached through YouTube's in-page navigation
//      (full page loads are already redirected by the declarativeNetRequest
//      rules in /rules), and
//   3. flags the few Shorts elements that can only be recognised by their
//      label (sidebar entry, search filter chip, channel tab).
(() => {
  "use strict";

  const root = document.documentElement;
  const HIDE_ATTR = "data-ysb-hide";
  const MARK_ATTR = "data-ysb-shorts";

  let settings = { ...YSB_DEFAULTS };
  // Redirects wait for the saved settings; hiding starts right away.
  let settingsLoaded = false;

  // ---------------------------------------------------------------------------
  // Redirects
  // ---------------------------------------------------------------------------

  const SHORT_VIDEO = /^\/shorts\/([A-Za-z0-9_-]+)/;
  const SHORTS_FEED = /^\/shorts\/?$/;
  const CHANNEL_SHORTS_TAB = /^(\/(?:@[^/]+|channel\/[^/]+|c\/[^/]+|user\/[^/]+))\/shorts\/?$/;

  /** Returns where `url` should go instead, or null to leave it alone. */
  function redirectTarget(url) {
    if (!settingsLoaded || !settings.enabled || settings.redirect === "off") return null;

    let u;
    try {
      u = new URL(url, location.href);
    } catch {
      return null;
    }
    if (!/(^|\.)youtube\.com$/.test(u.hostname)) return null;

    const channel = u.pathname.match(CHANNEL_SHORTS_TAB);
    if (channel) return `${u.origin}${channel[1]}/videos`;

    if (SHORTS_FEED.test(u.pathname)) return `${u.origin}/`;

    const video = u.pathname.match(SHORT_VIDEO);
    if (!video) return null;
    return settings.redirect === "home"
      ? `${u.origin}/`
      : `${u.origin}/watch?v=${video[1]}`;
  }

  let redirectingTo = null;

  function go(target, replace) {
    if (target === redirectingTo) return; // already on our way there
    redirectingTo = target;
    replace ? location.replace(target) : location.assign(target);
  }

  function checkLocation() {
    const target = redirectTarget(location.href);
    if (target && target !== location.href) go(target, true);
  }

  // Catch plain left-clicks on Shorts links before YouTube's router handles
  // them, so the Shorts player never starts. Middle/modifier clicks open a new
  // tab, which the declarativeNetRequest rules redirect on load.
  window.addEventListener(
    "click",
    (e) => {
      if (e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
      const link = e
        .composedPath()
        .find((el) => el instanceof HTMLAnchorElement && el.href.includes("/shorts"));
      if (!link) return;
      const target = redirectTarget(link.href);
      if (!target) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      go(target, false);
    },
    true
  );

  // YouTube is a single-page app: watch for its own navigation events too.
  // A capturing listener on window sees these wherever they are dispatched.
  for (const type of ["yt-navigate-start", "yt-navigate-finish", "popstate"]) {
    window.addEventListener(type, checkLocation, true);
  }

  // ---------------------------------------------------------------------------
  // Label-based hiding
  // ---------------------------------------------------------------------------

  // Elements whose only reliable Shorts signal is their label.
  const LABELLED = [
    "ytd-guide-entry-renderer", // desktop sidebar
    "ytd-mini-guide-entry-renderer", // collapsed sidebar
    "yt-chip-cloud-chip-renderer", // search filter chips
    "chip-shape", // newer chip component
    "yt-tab-shape", // channel page tabs
    "tp-yt-paper-tab", // older channel page tabs
    "ytm-pivot-bar-item-renderer", // m.youtube.com bottom bar
  ].join(",");

  function labelOf(el) {
    const own =
      el.getAttribute("tab-title") ||
      el.getAttribute("aria-label") ||
      el.getAttribute("title");
    if (own) return own;
    const titled = el.querySelector("[title]");
    if (titled) return titled.getAttribute("title");
    return el.textContent || "";
  }

  function markLabelled() {
    for (const el of document.querySelectorAll(LABELLED)) {
      const isShorts = labelOf(el).replace(/\s+/g, " ").trim().toLowerCase() === "shorts";
      // YouTube recycles elements, so re-check (and un-mark) every time.
      if (isShorts !== el.hasAttribute(MARK_ATTR)) el.toggleAttribute(MARK_ATTR, isShorts);
    }
  }

  // ---------------------------------------------------------------------------
  // Wiring
  // ---------------------------------------------------------------------------

  function hidingOn() {
    return settings.enabled && settings.hideShorts;
  }

  function apply() {
    if (root.hasAttribute(HIDE_ATTR) !== hidingOn()) root.toggleAttribute(HIDE_ATTR, hidingOn());
    checkLocation();
    scheduleScan();
  }

  let scanQueued = false;
  function scheduleScan() {
    if (scanQueued) return;
    scanQueued = true;
    setTimeout(() => {
      scanQueued = false;
      if (hidingOn()) markLabelled();
      checkLocation();
    }, 150);
  }

  new MutationObserver((mutations) => {
    for (const m of mutations) {
      // Put our attribute back if the page ever strips it from <html>.
      if (m.type === "attributes") {
        if (root.hasAttribute(HIDE_ATTR) !== hidingOn()) root.toggleAttribute(HIDE_ATTR, hidingOn());
        continue;
      }
      scheduleScan();
      return;
    }
  }).observe(root, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: [HIDE_ATTR],
  });

  // Hide immediately with the defaults so Shorts never flash on screen, then
  // correct once the saved settings arrive.
  apply();

  try {
    chrome.storage.sync.get(YSB_DEFAULTS, (saved) => {
      settings = { ...YSB_DEFAULTS, ...saved };
      settingsLoaded = true;
      apply();
    });
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area !== "sync") return;
      for (const [key, { newValue }] of Object.entries(changes)) {
        if (key in YSB_DEFAULTS) settings[key] = newValue ?? YSB_DEFAULTS[key];
      }
      apply();
    });
  } catch {
    // Extension was reloaded or removed while this tab was open; keep defaults.
  }
})();
