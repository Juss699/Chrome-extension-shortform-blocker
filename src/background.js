// Short-Form Blocker — service worker.
// Keeps the redirect rulesets and the toolbar badge in sync with the settings.
importScripts("defaults.js");

// Ruleset ids from manifest.json, per site and redirect mode.
const RULESETS = {
  shortsRedirect: { watch: "youtube_to_watch", home: "youtube_to_home" },
  reelsRedirect: { single: "instagram_to_single", home: "instagram_to_home" },
};

async function sync() {
  const settings = await chrome.storage.sync.get(YSB_DEFAULTS);

  const all = [];
  const active = [];
  for (const [key, byMode] of Object.entries(RULESETS)) {
    all.push(...Object.values(byMode));
    if (settings.enabled && byMode[settings[key]]) active.push(byMode[settings[key]]);
  }

  await chrome.declarativeNetRequest.updateEnabledRulesets({
    enableRulesetIds: active,
    disableRulesetIds: all.filter((id) => !active.includes(id)),
  });

  await chrome.action.setBadgeText({ text: settings.enabled ? "" : "off" });
  await chrome.action.setBadgeBackgroundColor({ color: "#6b7280" });
}

chrome.runtime.onInstalled.addListener(sync);
chrome.runtime.onStartup.addListener(sync);
chrome.storage.onChanged.addListener((_changes, area) => {
  if (area === "sync") sync();
});
