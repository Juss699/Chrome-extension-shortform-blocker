"""End-to-end tests: loads the extension in Chrome and points youtube.com at tests/mock_youtube.py."""
import asyncio, os, shutil, tempfile, sys
from playwright.async_api import async_playwright

EXT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), ".."))
CHROME = os.environ.get("CHROME_PATH")  # optional; defaults to Playwright's Chromium
results = []
def check(name, cond):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name)

async def visible(page, sel):
    return await page.evaluate(
        "s => { const e = document.querySelector(s); return !!e && getComputedStyle(e).display !== 'none'; }", sel)

async def main():
    ud = tempfile.mkdtemp()
    async with async_playwright() as p:
        ctx = await p.chromium.launch_persistent_context(
            ud, executable_path=CHROME, headless=True,
            ignore_https_errors=True,
            args=[f"--disable-extensions-except={EXT}", f"--load-extension={EXT}",
                  "--host-resolver-rules=MAP www.youtube.com:443 127.0.0.1:8443, MAP youtube.com:443 127.0.0.1:8443, MAP m.youtube.com:443 127.0.0.1:8443",
                  "--ignore-certificate-errors", "--no-proxy-server"])
        sw = ctx.service_workers[0] if ctx.service_workers else await ctx.wait_for_event("serviceworker", timeout=10000)
        ext_id = sw.url.split("/")[2]
        print("extension id", ext_id)
        await asyncio.sleep(0.5)

        page = await ctx.new_page()

        # --- Hiding on the home feed ---
        await page.goto("https://www.youtube.com/")
        await page.wait_for_timeout(1600)
        for sel in ["#g-shorts", "#mg-shorts", "#chip-shorts", "#tab-shorts", "#shelf", "#card-short", "#reel", "#late-short", "#late-chip"]:
            check(f"hidden {sel}", not await visible(page, sel))
        for sel in ["#g-home", "#chip-videos", "#tab-videos", "#card-normal"]:
            check(f"still visible {sel}", await visible(page, sel))

        # --- Hard-load redirects (declarativeNetRequest) ---
        await page.goto("https://www.youtube.com/shorts/abcDEF12345?feature=share")
        check("DNR /shorts/ID -> /watch?v=ID", page.url == "https://www.youtube.com/watch?v=abcDEF12345")
        await page.goto("https://youtube.com/shorts/noWWW000001")
        check("DNR bare domain", page.url == "https://youtube.com/watch?v=noWWW000001")
        await page.goto("https://www.youtube.com/shorts")
        check("DNR /shorts feed -> home", page.url == "https://www.youtube.com/")
        await page.goto("https://www.youtube.com/@somechannel/shorts")
        check("DNR channel shorts tab -> videos", page.url == "https://www.youtube.com/@somechannel/videos")

        # --- Click interception ---
        await page.goto("https://www.youtube.com/")
        await page.evaluate("document.querySelector('#card-short').setAttribute('style','display:block !important')")
        await page.evaluate("document.documentElement.removeAttribute('data-ysb-hide')")  # gets restored by observer
        await page.wait_for_timeout(100)
        check("html attribute restored after removal", await page.evaluate("document.documentElement.hasAttribute('data-ysb-hide')"))
        await page.evaluate("document.querySelector('#short-link').click()")
        await page.wait_for_url("**/watch?v=abcDEF12345", timeout=5000)
        check("click on Shorts link -> watch", page.url.endswith("/watch?v=abcDEF12345"))

        # --- SPA navigation ---
        await page.goto("https://www.youtube.com/")
        await page.wait_for_timeout(300)
        await page.evaluate("history.pushState({}, '', '/shorts/spaNav00001'); document.dispatchEvent(new Event('yt-navigate-finish'))")
        await page.wait_for_url("**/watch?v=spaNav00001", timeout=5000)
        check("SPA nav to Shorts -> watch", page.url.endswith("/watch?v=spaNav00001"))

        # --- Settings: turn hiding off, live ---
        await page.goto("https://www.youtube.com/")
        await page.wait_for_timeout(500)
        await sw.evaluate("chrome.storage.sync.set({hideShorts: false})")
        await page.wait_for_timeout(500)
        check("hideShorts=false shows shelf again", await visible(page, "#shelf"))
        check("hideShorts=false shows sidebar entry", await visible(page, "#g-shorts"))
        await sw.evaluate("chrome.storage.sync.set({hideShorts: true})")
        await page.wait_for_timeout(500)
        check("hideShorts=true hides again", not await visible(page, "#shelf"))

        # --- Redirect mode: home ---
        await sw.evaluate("chrome.storage.sync.set({redirect: 'home'})")
        await page.wait_for_timeout(500)
        await page.goto("https://www.youtube.com/shorts/toHome00001")
        check("redirect=home sends Shorts to homepage", page.url == "https://www.youtube.com/")

        # --- Redirect mode: off ---
        await sw.evaluate("chrome.storage.sync.set({redirect: 'off'})")
        await page.wait_for_timeout(500)
        await page.goto("https://www.youtube.com/shorts/stay0000001")
        await page.wait_for_timeout(500)
        check("redirect=off leaves Shorts URL alone", page.url == "https://www.youtube.com/shorts/stay0000001")

        # --- Master switch off ---
        await sw.evaluate("chrome.storage.sync.set({enabled: false, redirect: 'watch'})")
        await page.wait_for_timeout(500)
        badge = await sw.evaluate("chrome.action.getBadgeText({})")
        check("badge says off when disabled", badge == "off")
        await page.goto("https://www.youtube.com/")
        await page.wait_for_timeout(1200)
        check("disabled: shelf visible", await visible(page, "#shelf"))
        await page.goto("https://www.youtube.com/shorts/disabled001")
        await page.wait_for_timeout(500)
        check("disabled: no redirect", "/shorts/disabled001" in page.url)
        await sw.evaluate("chrome.storage.sync.set({enabled: true})")
        await page.wait_for_timeout(500)

        # --- Popup renders and saves ---
        pop = await ctx.new_page()
        await pop.set_viewport_size({"width": 320, "height": 200})
        await pop.goto(f"chrome-extension://{ext_id}/src/popup.html")
        await pop.wait_for_timeout(300)
        check("popup shows enabled", await pop.is_checked("#enabled"))
        await pop.click("label.radio:has(input[value='home'])")
        await pop.wait_for_timeout(200)
        check("popup saves redirect choice", (await sw.evaluate("chrome.storage.sync.get('redirect')"))["redirect"] == "home")
        await pop.click("label.radio:has(input[value='watch'])")
        await pop.wait_for_timeout(200)

        await ctx.close()
    shutil.rmtree(ud, ignore_errors=True)
    failed = [n for n, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)

asyncio.run(main())
