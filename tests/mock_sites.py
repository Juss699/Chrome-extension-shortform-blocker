"""Tiny HTTPS server that mimics the bits of YouTube's and Instagram's markup
the extension targets. Chrome is pointed at it with --host-resolver-rules."""
import http.server, ssl, sys

CERT_DIR = sys.argv[1]

YT_HOME = """<!doctype html><html><head><title>mock yt</title></head><body>
<ytd-guide-entry-renderer id="g-home"><a id="endpoint" title="Home">Home</a></ytd-guide-entry-renderer>
<ytd-guide-entry-renderer id="g-shorts"><a id="endpoint" title="Shorts">Shorts</a></ytd-guide-entry-renderer>
<ytd-mini-guide-entry-renderer id="mg-shorts" aria-label="Shorts">Shorts</ytd-mini-guide-entry-renderer>
<yt-chip-cloud-chip-renderer id="chip-shorts"><span>Shorts</span></yt-chip-cloud-chip-renderer>
<yt-chip-cloud-chip-renderer id="chip-videos"><span>Videos</span></yt-chip-cloud-chip-renderer>
<yt-tab-shape id="tab-shorts" tab-title="Shorts">Shorts</yt-tab-shape>
<yt-tab-shape id="tab-videos" tab-title="Videos">Videos</yt-tab-shape>
<ytd-rich-section-renderer id="shelf"><ytd-rich-shelf-renderer is-shorts>shelf</ytd-rich-shelf-renderer></ytd-rich-section-renderer>
<ytd-rich-item-renderer id="card-short"><a id="short-link" href="/shorts/abcDEF12345">a short</a></ytd-rich-item-renderer>
<ytd-rich-item-renderer id="card-normal"><a href="/watch?v=normal00001">normal video</a></ytd-rich-item-renderer>
<ytd-reel-shelf-renderer id="reel">reel</ytd-reel-shelf-renderer>
<div id="late"></div>
<script>
setTimeout(() => {
  document.getElementById('late').innerHTML =
    '<ytd-video-renderer id="late-short"><a href="/shorts/late0000001">late</a></ytd-video-renderer>' +
    '<yt-chip-cloud-chip-renderer id="late-chip"><span></span></yt-chip-cloud-chip-renderer>';
  setTimeout(() => { document.querySelector('#late-chip span').textContent = 'Shorts'; }, 300);
}, 800);
</script>
</body></html>"""

IG_NAV = """<nav>
<div><span><a id="nav-home" href="/">Home</a></span></div>
<div><span><a id="nav-explore" href="/explore/">Explore</a></span></div>
<div><span><a id="nav-reels" href="/reels/">Reels</a></span></div>
<div><span><a id="nav-dm" href="/direct/inbox/">Messages</a></span></div>
</nav>"""

IG_HOME = f"""<!doctype html><html><head><title>mock ig</title></head><body>{IG_NAV}
<main>
<article id="post-photo"><a href="/someuser/">someuser</a><a href="/p/PHOTO001/"><time>1h</time></a><img alt=""></article>
<article id="post-photo-music"><a href="/p/PHOTO002/"><time>2h</time></a><a href="/reels/audio/111/">Song</a><img alt=""></article>
<article id="post-reel"><a href="/reel/REEL0001/"><time>3h</time></a><video></video></article>
<article id="post-reel-audio"><a href="/p/REEL0002/"><time>4h</time></a><a href="/reels/audio/222/">Original audio</a><video></video></article>
<div id="late"></div>
</main>
<script>
// A reel that loads later and starts playing while hidden.
setTimeout(() => {{
  document.getElementById('late').innerHTML =
    '<article id="late-reel"><a href="/reel/LATE0001/"><time>5h</time></a><video id="late-video" muted></video></article>';
  const v = document.getElementById('late-video');
  const c = document.createElement('canvas'); c.width = c.height = 8;
  const g = c.getContext('2d'); setInterval(() => {{ g.fillStyle = '#' + Math.floor(Math.random()*999); g.fillRect(0,0,8,8); }}, 50);
  v.srcObject = c.captureStream(10);
  v.play().catch(() => {{}});
}}, 600);
</script>
</body></html>"""

IG_PROFILE = f"""<!doctype html><html><body>{IG_NAV}
<div role="tablist">
<a id="tab-posts" role="tab" href="/someuser/">Posts</a>
<a id="tab-reels" role="tab" href="/someuser/reels/">Reels</a>
<a id="tab-tagged" role="tab" href="/someuser/tagged/">Tagged</a>
</div></body></html>"""

IG_EXPLORE = f"""<!doctype html><html><body>{IG_NAV}
<main><a id="ex-reel" href="/reel/EXPL0001/">tile</a><a id="ex-post" href="/p/EXPL0002/">tile</a></main></body></html>"""

IG_SINGLE = f"""<!doctype html><html><body>{IG_NAV}
<article id="single"><a href="/reel/REEL0001/"><time>3h</time></a><video></video></article></body></html>"""

IG_DM = f"""<!doctype html><html><body>{IG_NAV}
<div id="thread"><a id="dm-reel" href="/reel/DMREEL01/">shared reel</a></div></body></html>"""

def page(host, path):
    path = path.split("?")[0]
    if "instagram" in host:
        if path == "/": return IG_HOME
        if path == "/someuser/": return IG_PROFILE
        if path.startswith("/explore"): return IG_EXPLORE
        if path.startswith("/reel/"): return IG_SINGLE
        if path.startswith("/direct/"): return IG_DM
    elif path in ("/", "/feed"):
        return YT_HOME
    return f"<!doctype html><html><body><h1 id='path'>{path}</h1></body></html>"

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        body = page(self.headers.get("Host", ""), self.path).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

class QuietServer(http.server.ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        pass  # the extension aborts redirected requests mid-response; that is expected

srv = QuietServer(("127.0.0.1", 8443), H)
ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ctx.load_cert_chain(f"{CERT_DIR}/cert.pem", f"{CERT_DIR}/key.pem")
srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
srv.serve_forever()
