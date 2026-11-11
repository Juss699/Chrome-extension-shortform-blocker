// Default settings, shared by the content script, the popup and the
// service worker. Declared with `var` so it becomes a global in all three.
var YSB_DEFAULTS = Object.freeze({
  // Master switch for the whole extension.
  enabled: true,

  // --- YouTube ---
  // Hide Shorts shelves, cards, the sidebar entry, search chip and channel tab.
  hideShorts: true,
  // What happens when a Shorts URL is opened:
  //   "watch" – play the same video in the regular player
  //   "home"  – go to the YouTube homepage instead
  //   "off"   – do nothing
  shortsRedirect: "watch",

  // --- Instagram ---
  // Hide the Reels tab, reel posts in the feed, reels in Explore and the
  // Reels tab on profiles.
  hideReels: true,
  // What happens when a Reels URL is opened:
  //   "single" – the swipeable Reels feed opens as just that one reel;
  //              single reels (e.g. sent to you in a DM) still open
  //   "home"   – every reel goes to the Instagram homepage
  //   "off"    – do nothing
  reelsRedirect: "single",
});
