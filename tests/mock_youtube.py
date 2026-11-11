"""Tiny HTTPS server that mimics the bits of YouTube's markup the extension targets."""
import http.server, ssl, sys

CERT_DIR = sys.argv[1]

HOME = """<!doctype html><html><head><title>mock yt</title></head><body>
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

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        body = (HOME if self.path in ("/", "/feed") else
                f"<!doctype html><html><body><h1 id='path'>{self.path}</h1></body></html>").encode()
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
