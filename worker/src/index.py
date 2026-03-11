import json
from urllib.parse import quote


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

    # ClickUp strips path segments from the registered redirect URI, so it delivers
    # the code to the root path. Accept both "/" and "/callback".
    if url.pathname not in ("/", "/callback"):
        return js.Response.new("Not found", status=404)

    code = url.searchParams.get("code")
    # state is a CSRF token verified by the Android app; encode it to prevent
    # query parameter injection if it contains '&' or '=' characters.
    state = quote(url.searchParams.get("state") or "", safe="")

    if not code:
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"error=missing_code&state={state}")),
            headers={"Content-Type": "text/html; charset=utf-8"},
        )

    try:
        resp = await js.fetch(
            "https://api.clickup.com/api/v2/oauth/token",
            method="POST",
            headers={"Content-Type": "application/json"},
            body=json.dumps({
                "client_id": env.CLICKUP_CLIENT_ID,
                "client_secret": env.CLICKUP_CLIENT_SECRET,
                "code": code,
            }),
        )

        if not resp.ok:
            return js.Response.new(
                build_deep_link_html(build_intent_url(f"error=token_exchange_failed&state={state}")),
                headers={"Content-Type": "text/html; charset=utf-8"},
            )

        data = await resp.json()
        token = quote(data.access_token, safe="")
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"token={token}&state={state}")),
            headers={"Content-Type": "text/html; charset=utf-8"},
        )

    except Exception:
        return js.Response.new(
            build_deep_link_html(build_intent_url(f"error=network_error&state={state}")),
            headers={"Content-Type": "text/html; charset=utf-8"},
        )
