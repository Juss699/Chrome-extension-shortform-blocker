// Shorts Blocker — service worker.
// Keeps the redirect rulesets and the toolbar badge in sync with the settings.
importScripts("defaults.js");

const RULESETS = {
  watch: "shorts_to_watch",
  home: "shorts_to_home",
};

async function sync() {
  const settings = await chrome.storage.sync.get(YSB_DEFAULTS);
  const active = settings.enabled ? RULESETS[settings.redirect] : undefined;

  await chrome.declarativeNetRequest.updateEnabledRulesets({
    enableRulesetIds: active ? [active] : [],
    disableRulesetIds: Object.values(RULESETS).filter((id) => id !== active),
  });

  await chrome.action.setBadgeText({ text: settings.enabled ? "" : "off" });
  await chrome.action.setBadgeBackgroundColor({ color: "#6b7280" });
}

chrome.runtime.onInstalled.addListener(sync);
chrome.runtime.onStartup.addListener(sync);
chrome.storage.onChanged.addListener((_changes, area) => {
  if (area === "sync") sync();
});
