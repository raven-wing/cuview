# Tests for the CU View OAuth Cloudflare Worker.
#
# Tests cover the pure helper functions. on_fetch is a thin adapter and is not tested here.
#
# Run with: uv run pytest

from src.index import build_intent_url, build_deep_link_html


# ── build_intent_url ───────────────────────────────────────────────────────────

def test_intent_url_contains_token_and_state():
    url = build_intent_url("token=pk_abc123&state=s1")
    assert "intent://oauth/callback?token=pk_abc123&state=s1" in url


def test_intent_url_contains_scheme_and_package():
    url = build_intent_url("token=x")
    assert "scheme=cuview" in url
    assert "package=io.github.raven_wing.cuview" in url


def test_intent_url_contains_launch_flags():
    url = build_intent_url("token=x")
    assert "launchFlags=0x30000000" in url


def test_intent_url_error_params():
    url = build_intent_url("error=missing_code&state=st")
    assert "error=missing_code&state=st" in url


# ── build_deep_link_html ───────────────────────────────────────────────────────

def test_html_contains_intent_url_as_href():
    html = build_deep_link_html("intent://oauth/callback?token=x#Intent;end")
    assert 'href="intent://oauth/callback?token=x#Intent;end"' in html


def test_html_autoclicks_link():
    html = build_deep_link_html("intent://x")
    assert 'document.getElementById("l").click()' in html


def test_html_escapes_double_quotes_in_url():
    html = build_deep_link_html('intent://x?a="b"')
    assert 'href="intent://x?a=&quot;b&quot;"' in html
