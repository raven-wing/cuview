import json
from urllib.parse import quote
from pyodide.ffi import to_js

# intent:// URLs are Chrome's guaranteed mechanism for firing Android intents from a
# browser tab. Custom scheme navigation (cuview://) is unreliable from Custom Tabs.
# Format: intent://HOST/PATH?QUERY#Intent;scheme=SCHEME;package=PACKAGE;end
#
# FLAG_ACTIVITY_NEW_TASK (0x10000000) brings the existing app task to front.
# FLAG_ACTIVITY_SINGLE_TOP (0x20000000) calls onNewIntent instead of creating a new instance.
def build_intent_url(params: str) -> str:
    return (
        f"intent://oauth/callback?{params}"
        f"#Intent;scheme=cuview;package=io.github.raven_wing.cuview;launchFlags=0x30000000;end"
    )


def build_deep_link_html(intent_url: str) -> str:
    href = intent_url.replace('"', "&quot;")
    return (
        '<!DOCTYPE html><html><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        '</head><body>'
        f'<a id="l" href="{href}">Open CU View</a>'
        '<script>document.getElementById("l").click();</script>'
        '</body></html>'
    )


async def on_fetch(request, env):
    import js
    url = js.URL.new(request.url)

    if request.method != "GET":
        return js.Response.new("Method Not Allowed", status=405)

    # ClickUp strips path segments from the registered redirect URI, so it delivers
    # the code to the root path. Accept both "/" and "/callback".
    if url.pathname not in ("/", "/callback"):
        return js.Response.new("Not found", status=404)

    code = url.searchParams.get("code")
    # state is a CSRF token verified by the Android app; encode it to prevent
    # query parameter injection if it contains '&' or '=' characters.
    raw_state = url.searchParams.get("state")
    state = quote(raw_state or "", safe="")

    html_headers = js.Headers.new()
    html_headers.set("Content-Type", "text/html; charset=utf-8")
    html_headers.set("Cache-Control", "no-store, max-age=0")
    html_headers.set("Pragma", "no-cache")

    if not raw_state:
        return js.Response.new(
            build_deep_link_html(build_intent_url("error=missing_state")),
            js.Object.fromEntries(to_js([("headers", html_headers)])),
        )

    if not code:
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"error=missing_code&state={state}")),
            js.Object.fromEntries(to_js([("headers", html_headers)])),
        )

    if code and (len(code) > 512 or not all(c.isalnum() or c in '-_.' for c in code)):
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"error=invalid_code&state={state}")),
            js.Object.fromEntries(to_js([("headers", html_headers)])),
        )

    try:
        headers = js.Headers.new()
        headers.set("Content-Type", "application/json")
        body = json.dumps({
            "client_id": env.CLICKUP_CLIENT_ID,
            "client_secret": env.CLICKUP_CLIENT_SECRET,
            "code": code,
        })
        init = js.Object.fromEntries(to_js([("method", "POST"), ("headers", headers), ("body", body)]))
        resp = await js.fetch("https://api.clickup.com/api/v2/oauth/token", init)

        if not resp.ok:
            js.console.error(f"ClickUp token exchange failed: HTTP {resp.status}")
            return js.Response.new(
                build_deep_link_html(build_intent_url(f"error=token_exchange_failed&state={state}")),
                js.Object.fromEntries(to_js([("headers", html_headers)])),
            )

        text = await resp.text()
        data = json.loads(text)
        access_token = data.get("access_token")
        if not access_token:
            return js.Response.new(
                build_deep_link_html(build_intent_url(f"error=token_exchange_failed&state={state}")),
                js.Object.fromEntries(to_js([("headers", html_headers)])),
            )
        token = quote(access_token, safe="")
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"token={token}&state={state}")),
            js.Object.fromEntries(to_js([("headers", html_headers)])),
        )

    except Exception:
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"error=network_error&state={state}")),
            js.Object.fromEntries(to_js([("headers", html_headers)])),
        )
