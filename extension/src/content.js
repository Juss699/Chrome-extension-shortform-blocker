// Short-Form Blocker — content script (YouTube and Instagram).
//
// Most hiding is done by the per-site stylesheet in src/sites/, which only
// applies while <html> has the `data-ysb-hide` attribute. This script:
//   1. toggles that attribute from the saved settings,
//   2. redirects Shorts/Reels URLs reached through in-page navigation (full
//      page loads are already redirected by the declarativeNetRequest rules
//      in /rules), and
//   3. handles the few things CSS can't: elements only recognisable by their
//      label, page-type hints for the stylesheet, and pausing hidden videos.
(() => {
  "use strict";

  const root = document.documentElement;
  const HIDE_ATTR = "data-ysb-hide"; // on <html> while hiding is active
  const PAGE_ATTR = "data-ysb-page"; // on <html>: what kind of page this is
  const MARK_ATTR = "data-ysb-shorts"; // on elements flagged by label

  // ---------------------------------------------------------------------------
  // Per-site rules
  // ---------------------------------------------------------------------------

  /** YouTube: where a Shorts path should go instead, or null. */
  function youtubeRedirect(path, mode) {
    const channel = path.match(/^(\/(?:@[^/]+|channel\/[^/]+|c\/[^/]+|user\/[^/]+))\/shorts\/?$/);
    if (channel) return `${channel[1]}/videos`;
    if (/^\/shorts\/?$/.test(path)) return "/";
    const video = path.match(/^\/shorts\/([A-Za-z0-9_-]+)/);
    if (!video) return null;
    return mode === "home" ? "/" : `/watch?v=${video[1]}`;
  }

  /** Instagram: where a Reels path should go instead, or null. */
  function instagramRedirect(path, mode) {
    // A profile's Reels tab -> the profile itself.
    const profileTab = path.match(/^(\/[A-Za-z0-9._]+)\/reels\/?$/);
    if (profileTab && profileTab[1] !== "/reels") return `${profileTab[1]}/`;
    // The Reels feed and "reels using this audio" pages -> home.
    if (/^\/reels\/?$/.test(path) || /^\/reels\/audio(\/|$)/.test(path)) return "/";
    // A reel inside the swipeable feed -> that reel on its own.
    const feedReel = path.match(/^\/reels\/([A-Za-z0-9_-]+)\/?$/);
    if (feedReel) return mode === "home" ? "/" : `/reel/${feedReel[1]}/`;
    // A single reel: only blocked in "home" mode.
    if (mode === "home" && /^\/(?:[A-Za-z0-9._]+\/)?reel\/[A-Za-z0-9_-]+/.test(path)) return "/";
    return null;
  }

  const SITES = [
    {
      host: /(^|\.)youtube\.com$/,
      hideKey: "hideShorts",
      redirectKey: "shortsRedirect",
      redirect: youtubeRedirect,
      // Elements whose only reliable Shorts signal is their label.
      labelled: [
        "ytd-guide-entry-renderer", // desktop sidebar
        "ytd-mini-guide-entry-renderer", // collapsed sidebar
        "yt-chip-cloud-chip-renderer", // search filter chips
        "chip-shape", // newer chip component
        "yt-tab-shape", // channel page tabs
        "tp-yt-paper-tab", // older channel page tabs
        "ytm-pivot-bar-item-renderer", // m.youtube.com bottom bar
      ].join(","),
    },
    {
      host: /(^|\.)instagram\.com$/,
      hideKey: "hideReels",
      redirectKey: "reelsRedirect",
      redirect: instagramRedirect,
      // Lets instagram.css keep a post visible on its own page, and target
      // Explore specifically.
      pageType(path) {
        if (/^\/(?:[A-Za-z0-9._]+\/)?(?:p|reel|tv)\/[^/]+/.test(path)) return "post";
        if (/^\/explore(\/|$)/.test(path)) return "explore";
        return "";
      },
      // A reel hidden mid-playback would otherwise keep playing unseen.
      pauseHiddenVideos: true,
    },
  ];

  const site = SITES.find((s) => s.host.test(location.hostname));
  if (!site) return;

  let settings = { ...YSB_DEFAULTS };
  // Redirects wait for the saved settings; hiding starts right away.
  let settingsLoaded = false;

  const hidingOn = () => settings.enabled && settings[site.hideKey];

  // ---------------------------------------------------------------------------
  // Redirects
  // ---------------------------------------------------------------------------

  /** Returns where `url` should go instead, or null to leave it alone. */
  function redirectTarget(url) {
    if (!settingsLoaded || !settings.enabled) return null;
    const mode = settings[site.redirectKey];
    if (mode === "off") return null;

    let u;
    try {
      u = new URL(url, location.href);
    } catch {
      return null;
    }
    if (!site.host.test(u.hostname)) return null;

    const path = site.redirect(u.pathname, mode);
    return path ? u.origin + path : null;
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

  // Catch plain left-clicks on links that would be redirected, before the
  // site's router handles them, so the Shorts/Reels player never starts.
  // Middle/modifier clicks open a new tab, which the declarativeNetRequest
  // rules redirect on load.
  window.addEventListener(
    "click",
    (e) => {
      if (e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
      const link = e.composedPath().find((el) => el instanceof HTMLAnchorElement && el.href);
      if (!link) return;
      const target = redirectTarget(link.href);
      if (!target) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      go(target, false);
    },
    true
  );

  // Both sites are single-page apps. YouTube announces navigations with its
  // own events; Instagram doesn't, so also watch the URL itself.
  for (const type of ["yt-navigate-start", "yt-navigate-finish", "popstate"]) {
    window.addEventListener(type, onUrlChange, true);
  }
  let lastHref = location.href;
  setInterval(() => {
    if (location.href !== lastHref) onUrlChange();
  }, 250);

  function onUrlChange() {
    lastHref = location.href;
    updatePageType();
    checkLocation();
  }

  // ---------------------------------------------------------------------------
  // Hiding helpers
  // ---------------------------------------------------------------------------

  function updatePageType() {
    if (!site.pageType) return;
    const type = site.pageType(location.pathname);
    if (root.getAttribute(PAGE_ATTR) !== type) root.setAttribute(PAGE_ATTR, type);
  }

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
    for (const el of document.querySelectorAll(site.labelled)) {
      const isShorts = labelOf(el).replace(/\s+/g, " ").trim().toLowerCase() === "shorts";
      // Sites recycle elements, so re-check (and un-mark) every time.
      if (isShorts !== el.hasAttribute(MARK_ATTR)) el.toggleAttribute(MARK_ATTR, isShorts);
    }
  }

  function pauseHiddenVideos() {
    for (const video of document.querySelectorAll("video")) {
      // A video inside a display:none ancestor has no layout boxes.
      if (!video.paused && video.getClientRects().length === 0) video.pause();
    }
  }

  // ---------------------------------------------------------------------------
  // Wiring
  // ---------------------------------------------------------------------------

  function syncHideAttr() {
    if (root.hasAttribute(HIDE_ATTR) !== hidingOn()) root.toggleAttribute(HIDE_ATTR, hidingOn());
  }

  function apply() {
    syncHideAttr();
    updatePageType();
    checkLocation();
    scheduleScan();
  }

  let scanQueued = false;
  function scheduleScan() {
    if (scanQueued) return;
    scanQueued = true;
    setTimeout(() => {
      scanQueued = false;
      if (hidingOn()) {
        if (site.labelled) markLabelled();
        if (site.pauseHiddenVideos) pauseHiddenVideos();
      }
      checkLocation();
    }, 150);
  }

  new MutationObserver((mutations) => {
    for (const m of mutations) {
      // Put our attribute back if the page ever strips it from <html>.
      if (m.type === "attributes") {
        syncHideAttr();
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

  // Instagram starts autoplaying feed videos as they scroll into view; catch
  // any that start while hidden.
  if (site.pauseHiddenVideos) {
    document.addEventListener(
      "play",
      (e) => {
        if (hidingOn() && e.target.getClientRects().length === 0) e.target.pause();
      },
      true
    );
  }

  // Hide immediately with the defaults so nothing flashes on screen, then
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
