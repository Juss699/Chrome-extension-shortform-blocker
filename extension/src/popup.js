// Short-Form Blocker — popup settings.
// Every control carries a data-key naming the setting it edits.
const controls = document.querySelectorAll("[data-key]");
const status = document.getElementById("status");

function render(settings) {
  for (const el of controls) {
    const value = settings[el.dataset.key];
    if (el.type === "checkbox") el.checked = value;
    else el.value = value;
  }
  document.body.classList.toggle("off", !settings.enabled);
  status.textContent = settings.enabled ? "Blocking Shorts and Reels" : "Paused";
}

for (const el of controls) {
  el.addEventListener("change", async () => {
    const value = el.type === "checkbox" ? el.checked : el.value;
    await chrome.storage.sync.set({ [el.dataset.key]: value });
    render(await chrome.storage.sync.get(YSB_DEFAULTS));
  });
}

chrome.storage.sync.get(YSB_DEFAULTS).then(render);
