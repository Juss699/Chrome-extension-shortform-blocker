// Default settings, shared by the content script, the popup and the
// service worker. Declared with `var` so it becomes a global in all three.
var YSB_DEFAULTS = Object.freeze({
  // Master switch for the whole extension.
  enabled: true,
  // Hide Shorts shelves, cards, the sidebar entry, search chip and channel tab.
  hideShorts: true,
  // What happens when a Shorts URL is opened:
  //   "watch" – open the same video in the regular player
  //   "home"  – go to the YouTube homepage instead
  //   "off"   – do nothing
  redirect: "watch",
});
