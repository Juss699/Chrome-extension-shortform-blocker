# Short-Form Blocker

A small Chrome extension that takes the endless short-video feeds out of **YouTube** and **Instagram**. Shorts and Reels disappear from your feeds, and links to them open without the swipe-forever player.

<img src="docs/popup.png" alt="Short-Form Blocker popup" width="320">

## What it does

### YouTube Shorts
- **Hidden:** Shorts shelves on the home page, subscriptions, search results, the watch-page sidebar, channel pages and history. Also the Shorts entry in the sidebar, the Shorts search filter, the Shorts tab on channels and Shorts in notifications. Covers `m.youtube.com` too.
- **Redirected:** `youtube.com/shorts/ID` plays as a normal video (`/watch?v=ID`), or goes to the homepage if you prefer. The Shorts feed goes to the homepage, and a channel's Shorts tab goes to its Videos tab.

### Instagram Reels
- **Hidden:** the Reels tab in the sidebar, reel posts in your home feed, reels in the Explore grid, and the Reels tab on profiles. Photo posts with music stay.
- **Redirected:** the Reels tab (`/reels/`) goes to the homepage. A reel opened inside the swipeable Reels feed (`/reels/ID`) opens as just that reel (`/reel/ID`), with no swiping on to the next one. A profile's Reels tab goes to the profile.
- **Still works:** a reel someone sends you in DMs still opens. Switch the redirect to "Go to homepage" to block single reels too.
- Any reel that starts playing while hidden is paused.

### Everywhere
- **One-click pause:** switch everything off from the toolbar popup. The icon shows an "off" badge while paused.
- **Per-site settings:** each site has its own hide switch and redirect choice.
- **Private:** no tracking and no network requests. Settings are stored with `chrome.storage.sync`.

## Install

The extension isn't on the Chrome Web Store, so you load it yourself (takes a minute):

1. Download this repo: **Code → Download ZIP**, then unzip it (or `git clone` it).
2. Open `chrome://extensions` in Chrome.
3. Turn on **Developer mode** (top right).
4. Click **Load unpacked** and pick the folder that contains `manifest.json`.
5. Pin the extension from the puzzle-piece menu if you want the toggle handy.

Works in any Chromium browser that supports Manifest V3: Chrome, Edge, Brave, Opera, Vivaldi.

To update after pulling new changes, hit the reload icon on the extension's card in `chrome://extensions`, then refresh any open YouTube or Instagram tabs.

## Settings

| Setting | Options |
| --- | --- |
| Master switch | On / off (top-right toggle in the popup) |
| YouTube: Hide Shorts | On / off |
| YouTube: Shorts links | Play in normal player · Go to homepage · Leave alone |
| Instagram: Hide Reels | On / off |
| Instagram: Reels links | Show just that reel · Go to homepage · Leave alone |

## How it works

| Part | File | Job |
| --- | --- | --- |
| Hiding | `src/sites/youtube.css`, `src/sites/instagram.css` | CSS rules that hide Shorts and Reels. All scoped to `html[data-ysb-hide]`, so turning a site off restores the page instantly. |
| Content script | `src/content.js` | Sets that attribute from your settings. Catches clicks and in-page navigation to Shorts/Reels. Flags YouTube elements that can only be recognised by their "Shorts" label, and pauses Instagram videos that were hidden mid-play. |
| Redirect rules | `rules/*.json` | `declarativeNetRequest` rules that rewrite Shorts/Reels URLs before the page loads (typed URLs, links from other sites, new tabs). One ruleset per site and redirect mode. |
| Service worker | `src/background.js` | Switches the rulesets on and off to match your settings, and sets the toolbar badge. |
| Popup | `src/popup.*` | The settings panel. |

### Permissions

- `storage`: to save your settings.
- `declarativeNetRequest` + access to `youtube.com` and `instagram.com`: to redirect Shorts/Reels URLs and hide them on those sites. The extension runs on no other site.

## When a site changes its layout

YouTube and Instagram both change their page markup regularly, which can let Shorts or Reels creep back in somewhere. To fix it:

1. Right-click the element → **Inspect**.
2. Find something stable to target. On YouTube that's a tag name like `ytd-…`, `yt-…` or `ytm-…`. On Instagram, class names are random, so use link targets (`a[href*="/reel/"]`) and tags like `article`.
3. Add a selector to the site's file in `src/sites/`, scoped with `html[data-ysb-hide]` like the others.
4. Reload the extension and open a pull request.

## Development

There are no build steps. Edit the files, then reload the extension.

End-to-end tests load the extension in headless Chrome and point `youtube.com` and `instagram.com` at local mock pages (`tests/mock_sites.py`). They check hiding, every redirect mode, in-page navigation, settings changes and the popup:

```bash
pip install playwright
playwright install chromium
./tests/run.sh
```

Set `CHROME_PATH` to use a specific Chrome for Testing or Chromium build. Branded Google Chrome 137+ no longer allows loading extensions from the command line, so use Chromium or Chrome for Testing.

## Project structure

```
manifest.json          Extension manifest (MV3)
src/
  defaults.js          Default settings, shared by every script
  content.js           Content script (both sites)
  sites/               Hiding rules per site
  background.js        Service worker
  popup.html/.css/.js  Settings popup
rules/                 declarativeNetRequest redirect rulesets
icons/                 Toolbar and store icons
tests/                 End-to-end tests
```

## License

[MIT](LICENSE). Not affiliated with or endorsed by YouTube, Google, Instagram or Meta.
