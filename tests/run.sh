#!/usr/bin/env bash
# Runs the end-to-end tests. Needs: python3, openssl, `pip install playwright`
# and `playwright install chromium` (or set CHROME_PATH to a Chrome for Testing build).
set -euo pipefail
cd "$(dirname "$0")"
TMP="$(mktemp -d)"
trap 'kill "$SERVER" 2>/dev/null || true; rm -rf "$TMP"' EXIT
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$TMP/key.pem" -out "$TMP/cert.pem" \
  -days 1 -subj "/CN=www.youtube.com" 2>/dev/null
python3 mock_sites.py "$TMP" &
SERVER=$!
sleep 1
python3 e2e.py ..
