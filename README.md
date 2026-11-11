# Short-Form Blocker

Blocks **YouTube Shorts** and **Instagram Reels**, on your computer and on your Android phone.

| | What you get |
| --- | --- |
| [**Android app**](#android-app) | Closes Shorts and Reels in the YouTube and Instagram apps as soon as they open. |
| [**Chrome extension**](#chrome-extension) | Removes Shorts and Reels from youtube.com and instagram.com, and opens their links without the swipe feed. |

---

## Android app

**[Download ShortFormBlocker.apk](https://github.com/Juss699/Chrome-extension-shortform-blocker/releases/latest/download/ShortFormBlocker.apk)** (Android 8.0 or newer)

### What it does
- **Hides the Shorts button** in the YouTube app and the **Reels button** in the Instagram app. It covers each one with a patch in the navigation bar's colour that swallows taps. Android doesn't let one app remove parts of another app, so this is the closest equivalent; a gap is left where the button was.
- **Optional: closes Shorts and Reels opened another way**, for example from a thumbnail in the feed. It pauses them and presses Back, staying inside the app, and also pauses them if they shrink into the miniplayer or a picture-in-picture window. This is off by default; turn it on in the app.
- Switch YouTube and Instagram on or off separately, or **pause for 5 minutes**.
- Counts how many times it has blocked Shorts and Reels.
- Needs no internet permission and has no ads or tracking. It only receives events from the YouTube and Instagram apps.

Shorts and Reels thumbnails inside the feeds can't be covered: they move as you scroll, and a cover would lag behind. Turn on the close option if you want those blocked too.

### Install
1. On your phone, tap the download link above.
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**. If Play Protect warns about an unrecognised app, tap **More details → Install anyway**. It warns because the app isn't from the Play Store.
3. Open **Short-Form Blocker** and tap **Open Accessibility settings**. Find Short-Form Blocker (often under "Downloaded apps" or "Installed apps") and turn it on.
4. **Is the switch greyed out ("Restricted setting")?** Android 13 and newer do that for apps installed outside the Play Store. Go to **Settings → Apps → Short-Form Blocker**, tap **⋮** in the top corner, choose **Allow restricted settings**, then go back to step 3.

When it says **Blocking is on**, you're done.

**Updating:** download and install the new APK over the old one. If Android says the app conflicts with an existing one, uninstall the old version first. That happens if your copy is from before version 1.1.0, or if the build's signing key expired after months without a build.

### If a button isn't hidden, or a Short or Reel gets through
YouTube and Instagram sometimes rename their screens in app updates. To find the new names:
1. In Short-Form Blocker, turn on **Record screen layout**.
2. Open YouTube or Instagram where the button wasn't hidden (or open the Short or Reel that got through), then come back to Short-Form Blocker.
3. Tap **Share report** and send it to whoever maintains this repo (or open an issue). The report lists layout names like `reel_recycler` and the labels of the bottom navigation buttons, never content.

The fix is usually a one-line change in [`Platform.kt`](android/app/src/main/java/io/github/juss699/shortformblocker/Platform.kt).

### How it works
`BlockerService` is an [accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service) subscribed only to `com.google.android.youtube` and `com.instagram.android`.

- **Hiding the button** ([`TabCovers.kt`](android/app/src/main/java/io/github/juss699/shortformblocker/TabCovers.kt)): it finds the Shorts/Reels tab in the bottom navigation bar. Instagram's has a known id (`clips_tab`). Otherwise it looks for a small tab labelled exactly "Shorts"/"Reels" at the bottom of the window, in a row of at least three tabs. Then it draws a `TYPE_ACCESSIBILITY_OVERLAY` window exactly over it. The cover is the bar's colour: on Android 11+ it reads one pixel beside the button with `takeScreenshot`, and otherwise it guesses from light/dark mode. The cover is removed whenever the app isn't in front or something (keyboard, notification shade, another app) is on top of the button.
- **Closing players (optional):** when a view that only exists in the full-screen Shorts/Reels player (`reel_recycler`, `reel_player_page_container`, `clips_viewer_view_pager`) fills the screen, it pauses playback (media pause key plus briefly taking audio focus), then presses Back. It waits 1.5 s before retrying, and after two Backs it opens the app's home feed instead. It never presses Home, because that sends a playing video into picture-in-picture. Shrunk players are paused.

### Build it yourself
GitHub Actions builds the APK on every change to `android/` and publishes it as the [`android-latest` release](https://github.com/Juss699/Chrome-extension-shortform-blocker/releases/tag/android-latest). To build locally with Android Studio or the SDK installed:

```bash
cd android
./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

---

## Chrome extension

<img src="docs/popup.png" alt="Short-Form Blocker popup" width="300">

### YouTube Shorts
- **Hidden:** Shorts shelves on the home page, subscriptions, search results, the watch-page sidebar, channel pages and history. Also the Shorts sidebar entry, the Shorts search filter, the Shorts channel tab and Shorts in notifications. Covers `m.youtube.com` too.
- **Redirected:** `/shorts/ID` plays in the normal player (`/watch?v=ID`), or goes to the homepage if you choose. The Shorts feed goes home, and a channel's Shorts tab goes to its Videos tab.

### Instagram Reels
- **Hidden:** the Reels tab, reel posts in your home feed, reels in Explore, and the Reels tab on profiles. Photo posts with music stay.
- **Redirected:** the Reels tab goes home. A reel inside the swipe feed (`/reels/ID`) opens on its own (`/reel/ID`), and a profile's Reels tab goes to the profile. Reels sent to you in DMs still open, unless you set links to "Go to homepage".
- Reels that start playing while hidden are paused.

### Install
1. Download this repo (**Code → Download ZIP**) and unzip it, or `git clone` it.
2. Open `chrome://extensions` and turn on **Developer mode**.
3. Click **Load unpacked** and choose the **`extension`** folder.

Works in Chrome, Edge, Brave, Opera and Vivaldi. After pulling changes, click the reload icon on the extension's card and refresh any open YouTube or Instagram tabs.

### How it works
| Part | File | Job |
| --- | --- | --- |
| Hiding | `extension/src/sites/*.css` | CSS rules scoped to `html[data-ysb-hide]`, so switching a site off restores the page instantly. |
| Content script | `extension/src/content.js` | Toggles that attribute from your settings and catches clicks and in-page navigation to Shorts/Reels. Also flags YouTube elements only recognisable by their label, and pauses hidden Instagram videos. |
| Redirect rules | `extension/rules/*.json` | `declarativeNetRequest` rules that rewrite Shorts/Reels URLs before the page loads. |
| Service worker | `extension/src/background.js` | Turns the rulesets on and off to match your settings, and shows the "off" badge. |
| Popup | `extension/src/popup.*` | Settings: master switch, plus a hide switch and a link option for each site. |

Permissions: `storage`, plus `declarativeNetRequest` and access to youtube.com and instagram.com only. No tracking, no network requests.

### When a site changes its layout
Inspect the element that slipped through and add a selector to the site's file in `extension/src/sites/`, scoped with `html[data-ysb-hide]`. On YouTube, target tag names (`ytd-…`, `yt-…`, `ytm-…`). On Instagram, class names are random, so use link targets (`a[href*="/reel/"]`) and tags like `article`.

### Tests
End-to-end tests load the extension in headless Chromium against local mock pages of both sites. They check hiding, every redirect mode, in-page navigation, settings and the popup:

```bash
pip install playwright && playwright install chromium
./extension/tests/run.sh
```

---

## Repository layout

```
android/      Android app (Kotlin, no third-party dependencies)
extension/    Chrome extension (Manifest V3)
docs/         Screenshots
.github/      Workflow that builds and publishes the APK
```

## License

[MIT](LICENSE). Not affiliated with or endorsed by YouTube, Google, Instagram or Meta.
