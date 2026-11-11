"""End-to-end tests: loads the extension in Chrome and points youtube.com and
instagram.com at tests/mock_sites.py."""
import asyncio, os, shutil, tempfile, sys
from playwright.async_api import async_playwright

EXT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), ".."))
CHROME = os.environ.get("CHROME_PATH")  # optional; defaults to Playwright's Chromium
HOSTS = ["youtube.com", "www.youtube.com", "m.youtube.com", "instagram.com", "www.instagram.com"]
YT = "https://www.youtube.com"
IG = "https://www.instagram.com"

results = []


def check(name, cond):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name)


async def visible(page, sel):
    return await page.evaluate(
        "s => { const e = document.querySelector(s); return !!e && e.getClientRects().length > 0; }", sel)


async def settle(page, ms=500):
    await page.wait_for_timeout(ms)


async def youtube_tests(page, sw):
    print("\n-- YouTube --")
    await page.goto(f"{YT}/")
    await settle(page, 1600)
    for sel in ["#g-shorts", "#mg-shorts", "#chip-shorts", "#tab-shorts", "#shelf", "#card-short", "#reel", "#late-short", "#late-chip"]:
        check(f"yt hidden {sel}", not await visible(page, sel))
    for sel in ["#g-home", "#chip-videos", "#tab-videos", "#card-normal"]:
        check(f"yt still visible {sel}", await visible(page, sel))

    await page.goto(f"{YT}/shorts/abcDEF12345?feature=share")
    check("yt DNR /shorts/ID -> /watch?v=ID", page.url == f"{YT}/watch?v=abcDEF12345")
    await page.goto("https://youtube.com/shorts/noWWW000001")
    check("yt DNR bare domain", page.url == "https://youtube.com/watch?v=noWWW000001")
    await page.goto(f"{YT}/shorts")
    check("yt DNR /shorts feed -> home", page.url == f"{YT}/")
    await page.goto(f"{YT}/@somechannel/shorts")
    check("yt DNR channel shorts tab -> videos", page.url == f"{YT}/@somechannel/videos")

    await page.goto(f"{YT}/")
    await page.evaluate("document.documentElement.removeAttribute('data-ysb-hide')")
    await page.wait_for_timeout(100)
    check("yt html attribute restored after removal", await page.evaluate("document.documentElement.hasAttribute('data-ysb-hide')"))
    await page.evaluate("document.querySelector('#short-link').click()")
    await page.wait_for_url("**/watch?v=abcDEF12345", timeout=5000)
    check("yt click on Shorts link -> watch", page.url.endswith("/watch?v=abcDEF12345"))

    await page.goto(f"{YT}/")
    await settle(page, 300)
    await page.evaluate("history.pushState({}, '', '/shorts/spaNav00001'); document.dispatchEvent(new Event('yt-navigate-finish'))")
    await page.wait_for_url("**/watch?v=spaNav00001", timeout=5000)
    check("yt SPA nav to Shorts -> watch", page.url.endswith("/watch?v=spaNav00001"))

    await page.goto(f"{YT}/")
    await settle(page)
    await sw.evaluate("chrome.storage.sync.set({hideShorts: false})")
    await settle(page)
    check("yt hideShorts=false shows shelf again", await visible(page, "#shelf"))
    check("yt hideShorts=false shows sidebar entry", await visible(page, "#g-shorts"))
    await sw.evaluate("chrome.storage.sync.set({hideShorts: true})")
    await settle(page)
    check("yt hideShorts=true hides again", not await visible(page, "#shelf"))

    await sw.evaluate("chrome.storage.sync.set({shortsRedirect: 'home'})")
    await settle(page)
    await page.goto(f"{YT}/shorts/toHome00001")
    check("yt redirect=home sends Shorts to homepage", page.url == f"{YT}/")

    await sw.evaluate("chrome.storage.sync.set({shortsRedirect: 'off'})")
    await settle(page)
    await page.goto(f"{YT}/shorts/stay0000001")
    await settle(page)
    check("yt redirect=off leaves Shorts URL alone", page.url == f"{YT}/shorts/stay0000001")
    await sw.evaluate("chrome.storage.sync.set({shortsRedirect: 'watch'})")
    await settle(page)


async def instagram_tests(page, sw):
    print("\n-- Instagram --")
    await page.goto(f"{IG}/")
    await settle(page, 1200)
    for sel in ["#nav-reels", "#post-reel", "#post-reel-audio", "#late-reel"]:
        check(f"ig hidden {sel}", not await visible(page, sel))
    for sel in ["#nav-home", "#nav-explore", "#nav-dm", "#post-photo", "#post-photo-music"]:
        check(f"ig still visible {sel}", await visible(page, sel))
    check("ig reel that starts playing while hidden gets paused",
          await page.evaluate("(() => { const v = document.getElementById('late-video'); return !!v && v.paused; })()"))

    await page.goto(f"{IG}/someuser/")
    await settle(page, 300)
    check("ig profile Reels tab hidden", not await visible(page, "#tab-reels"))
    check("ig profile Posts tab visible", await visible(page, "#tab-posts"))

    await page.goto(f"{IG}/explore/")
    await settle(page, 300)
    check("ig Explore reel tile hidden", not await visible(page, "#ex-reel"))
    check("ig Explore photo tile visible", await visible(page, "#ex-post"))

    await page.goto(f"{IG}/reel/REEL0001/")
    await settle(page, 300)
    check("ig single reel page stays and is visible", page.url == f"{IG}/reel/REEL0001/" and await visible(page, "#single"))

    await page.goto(f"{IG}/direct/inbox/")
    await settle(page, 300)
    check("ig reel shared in DMs stays visible", await visible(page, "#dm-reel"))
    await page.evaluate("document.querySelector('#dm-reel').click()")
    await page.wait_for_url("**/reel/DMREEL01/", timeout=5000)
    check("ig opening a DM'd reel works", page.url == f"{IG}/reel/DMREEL01/")

    # Hard loads (declarativeNetRequest)
    await page.goto(f"{IG}/reels/")
    check("ig DNR /reels/ -> home", page.url == f"{IG}/")
    await page.goto(f"{IG}/reels/ABCdef_123/?igsh=x")
    check("ig DNR /reels/ID -> /reel/ID", page.url == f"{IG}/reel/ABCdef_123/")
    await page.goto("https://instagram.com/reels/NOWWW0001/")
    check("ig DNR bare domain", page.url == "https://instagram.com/reel/NOWWW0001/")
    await page.goto(f"{IG}/reels/audio/123456/")
    check("ig DNR reels audio page -> home", page.url == f"{IG}/")
    await page.goto(f"{IG}/someuser/reels/")
    check("ig DNR profile Reels tab -> profile", page.url == f"{IG}/someuser/")

    # Clicks and in-page navigation
    await page.goto(f"{IG}/")
    await settle(page, 300)
    await page.evaluate("document.querySelector('#nav-reels').click()")
    await settle(page, 800)
    check("ig click on Reels tab -> home", page.url == f"{IG}/")
    await page.evaluate("history.pushState({}, '', '/reels/SPANAV001/')")  # no event, like Instagram
    await page.wait_for_url("**/reel/SPANAV001/", timeout=5000)
    check("ig SPA nav into Reels feed -> single reel", page.url == f"{IG}/reel/SPANAV001/")

    # Settings
    await page.goto(f"{IG}/")
    await settle(page)
    await sw.evaluate("chrome.storage.sync.set({hideReels: false})")
    await settle(page)
    check("ig hideReels=false shows reel posts", await visible(page, "#post-reel"))
    check("ig hideReels=false shows Reels tab", await visible(page, "#nav-reels"))
    check("ig hideReels=false leaves YouTube setting alone",
          (await sw.evaluate("chrome.storage.sync.get(null)")).get("hideShorts", True) is True)
    await page.reload()
    await settle(page, 1200)
    check("ig control: same late reel plays when hiding is off",
          await page.evaluate("(() => { const v = document.getElementById('late-video'); return !!v && !v.paused; })()"))
    await sw.evaluate("chrome.storage.sync.set({hideReels: true})")
    await settle(page)
    check("ig hideReels=true hides again", not await visible(page, "#post-reel"))

    await sw.evaluate("chrome.storage.sync.set({reelsRedirect: 'home'})")
    await settle(page)
    await page.goto(f"{IG}/reel/ANYREEL01/")
    check("ig redirect=home blocks single reels", page.url == f"{IG}/")
    await page.goto(f"{IG}/someuser/reel/ANYREEL02/")
    check("ig redirect=home blocks user/reel/ID", page.url == f"{IG}/")
    await page.goto(f"{IG}/reels/ANYREEL03/")
    check("ig redirect=home blocks Reels feed", page.url == f"{IG}/")
    await page.goto(f"{IG}/")
    await settle(page, 300)
    await page.evaluate("history.pushState({}, '', '/reel/SPAHOME01/')")
    await page.wait_for_url(f"{IG}/", timeout=5000)
    await settle(page, 300)
    check("ig redirect=home catches in-page nav to a reel", page.url == f"{IG}/")

    await sw.evaluate("chrome.storage.sync.set({reelsRedirect: 'off'})")
    await settle(page)
    await page.goto(f"{IG}/reels/")
    await settle(page)
    check("ig redirect=off leaves Reels URL alone", page.url == f"{IG}/reels/")
    await sw.evaluate("chrome.storage.sync.set({reelsRedirect: 'single'})")
    await settle(page)


async def master_switch_tests(page, sw):
    print("\n-- Master switch --")
    await sw.evaluate("chrome.storage.sync.set({enabled: false})")
    await settle(page)
    check("badge says off when disabled", await sw.evaluate("chrome.action.getBadgeText({})") == "off")
    await page.goto(f"{YT}/")
    await settle(page, 1200)
    check("disabled: yt shelf visible", await visible(page, "#shelf"))
    await page.goto(f"{YT}/shorts/disabled001")
    await settle(page)
    check("disabled: yt no redirect", "/shorts/disabled001" in page.url)
    await page.goto(f"{IG}/")
    await settle(page, 800)
    check("disabled: ig reel post visible", await visible(page, "#post-reel"))
    await page.goto(f"{IG}/reels/")
    await settle(page)
    check("disabled: ig no redirect", page.url == f"{IG}/reels/")
    await sw.evaluate("chrome.storage.sync.set({enabled: true})")
    await settle(page)
    check("badge cleared when re-enabled", await sw.evaluate("chrome.action.getBadgeText({})") == "")


async def popup_tests(ctx, sw, ext_id, screenshot):
    print("\n-- Popup --")
    pop = await ctx.new_page()
    await pop.set_viewport_size({"width": 320, "height": 200})
    await pop.goto(f"chrome-extension://{ext_id}/src/popup.html")
    await pop.wait_for_timeout(300)
    check("popup shows enabled", await pop.is_checked("[data-key=enabled]"))
    check("popup shows YouTube default", await pop.input_value("[data-key=shortsRedirect]") == "watch")
    check("popup shows Instagram default", await pop.input_value("[data-key=reelsRedirect]") == "single")
    await pop.select_option("[data-key=reelsRedirect]", "home")
    await pop.wait_for_timeout(200)
    check("popup saves Instagram redirect", (await sw.evaluate("chrome.storage.sync.get('reelsRedirect')"))["reelsRedirect"] == "home")
    await pop.click("label.row:has([data-key=hideShorts])")
    await pop.wait_for_timeout(200)
    check("popup toggles Hide Shorts", (await sw.evaluate("chrome.storage.sync.get('hideShorts')"))["hideShorts"] is False)
    await pop.click("label.row:has([data-key=hideShorts])")
    await pop.select_option("[data-key=reelsRedirect]", "single")
    await pop.wait_for_timeout(200)
    if screenshot:
        await pop.screenshot(path=screenshot, full_page=True)
    await pop.close()


async def main():
    ud = tempfile.mkdtemp()
    rules = ", ".join(f"MAP {h}:443 127.0.0.1:8443" for h in HOSTS)
    async with async_playwright() as p:
        ctx = await p.chromium.launch_persistent_context(
            ud, executable_path=CHROME, headless=True, ignore_https_errors=True,
            args=[f"--disable-extensions-except={EXT}", f"--load-extension={EXT}",
                  f"--host-resolver-rules={rules}", "--ignore-certificate-errors",
                  "--no-proxy-server", "--autoplay-policy=no-user-gesture-required"])
        sw = ctx.service_workers[0] if ctx.service_workers else await ctx.wait_for_event("serviceworker", timeout=10000)
        ext_id = sw.url.split("/")[2]
        await asyncio.sleep(0.5)

        page = await ctx.new_page()
        await youtube_tests(page, sw)
        await instagram_tests(page, sw)
        await master_switch_tests(page, sw)
        await popup_tests(ctx, sw, ext_id, os.environ.get("POPUP_SCREENSHOT"))
        await ctx.close()
    shutil.rmtree(ud, ignore_errors=True)
    shutil.rmtree(os.path.join(EXT, "_metadata"), ignore_errors=True)  # Chrome's ruleset cache

    failed = [n for n, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


asyncio.run(main())
