#!/usr/bin/env python3
"""
Local mock OAuth server for E2E tests.

Wraps the Cloudflare Worker's pure-Python helpers so that any change to the
intent:// URL format or HTML template stays in sync automatically.

Reached from inside the emulator via `localhost:8765`, with `adb reverse
tcp:8765 tcp:8765` set up by the Makefile to forward that to the host's 8765.
We deliberately use adb-reverse instead of the 10.0.2.2 emulator-NAT path
because the latter is unreliable on headless CI emulators (-no-window):
requests hang and Chrome never renders the auth page.

Port must match the URL hard-coded in ConfigScreen.kt and `adb reverse` in
the Makefile's mock-oauth-start target.
"""
import os
import sys

# Import shared helpers directly from the Worker source.
# build_intent_url / build_deep_link_html are pure Python; on_fetch is not (JS interop).
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "worker"))
from src.index import build_deep_link_html, build_intent_url  # noqa: E402

from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, quote, urlparse


_AUTH_PAGE = """\
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Mock ClickUp OAuth</title>
<style>body{{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0}}
a{{display:block;padding:16px 32px;background:#7b68ee;color:#fff;text-decoration:none;border-radius:8px;font-size:18px}}</style>
</head>
<body><a href="/callback?state={state}&token=mock_token">Connect Workspace</a></body>
</html>
"""


class _Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        if parsed.path == "/callback":
            # Step 2: fire the intent:// deep-link (mirrors Cloudflare Worker response).
            raw_state = qs.get("state", [""])[0]
            state = quote(raw_state, safe="")
            params = f"token=mock_token&state={state}" if raw_state else "error=missing_state"
            body = build_deep_link_html(build_intent_url(params)).encode()
        else:
            # Step 1: authorization page — user (or Maestro) taps "Connect Workspace".
            raw_state = qs.get("state", [""])[0]
            state = quote(raw_state, safe="")
            body = _AUTH_PAGE.format(state=state).encode()

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store, max-age=0")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    server = HTTPServer(("127.0.0.1", port), _Handler)
    print(f"mock OAuth server on :{port}", flush=True)
    server.serve_forever()
