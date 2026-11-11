// Shorts Blocker — popup settings.
const enabledBox = document.getElementById("enabled");
const hideBox = document.getElementById("hideShorts");
const radios = document.querySelectorAll('input[name="redirect"]');
const status = document.getElementById("status");

function render(settings) {
  enabledBox.checked = settings.enabled;
  hideBox.checked = settings.hideShorts;
  for (const radio of radios) radio.checked = radio.value === settings.redirect;
  document.body.classList.toggle("off", !settings.enabled);
  status.textContent = settings.enabled ? "Blocking Shorts on YouTube" : "Paused";
}

async function save(patch) {
  await chrome.storage.sync.set(patch);
  render(await chrome.storage.sync.get(YSB_DEFAULTS));
}

enabledBox.addEventListener("change", () => save({ enabled: enabledBox.checked }));
hideBox.addEventListener("change", () => save({ hideShorts: hideBox.checked }));
for (const radio of radios) {
  radio.addEventListener("change", () => save({ redirect: radio.value }));
}

chrome.storage.sync.get(YSB_DEFAULTS).then(render);
