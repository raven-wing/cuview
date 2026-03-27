package io.github.raven_wing.cuview.ui.config

import android.net.Uri
import io.github.raven_wing.cuview.ui.connect.ConnectActivity
import io.github.raven_wing.cuview.ui.connect.OAuthResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Regression tests for ConnectActivity.parseOAuthCallback — the function that
// extracts an OAuthResult from the cuview://oauth/callback deep-link URI delivered
// by the Cloudflare Worker after the ClickUp authorization flow.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OAuthCallbackParsingTest {

    @Test
    fun validToken_returnsSuccess() {
        val uri = Uri.parse("cuview://oauth/callback?token=pk_abc123")
        assertEquals(OAuthResult.Success("pk_abc123"),
            ConnectActivity.parseOAuthCallback(uri))
    }

    @Test
    fun errorParam_returnsFailure() {
        val uri = Uri.parse("cuview://oauth/callback?error=token_exchange_failed")
        assertEquals(OAuthResult.Failure("token_exchange_failed"),
            ConnectActivity.parseOAuthCallback(uri))
    }

    @Test
    fun encodedToken_decodesCorrectly() {
        val uri = Uri.parse("cuview://oauth/callback?token=pk%2Babc%2F123")
        assertEquals(OAuthResult.Success("pk+abc/123"),
            ConnectActivity.parseOAuthCallback(uri))
    }

    @Test
    fun wrongScheme_returnsNull() {
        val uri = Uri.parse("https://cuview-oauth.ravenwing.workers.dev/callback?token=abc")
        assertNull(ConnectActivity.parseOAuthCallback(uri))
    }

    @Test
    fun wrongHost_returnsNull() {
        val uri = Uri.parse("cuview://other/callback?token=abc")
        assertNull(ConnectActivity.parseOAuthCallback(uri))
    }

    @Test
    fun noTokenOrError_returnsNull() {
        val uri = Uri.parse("cuview://oauth/callback")
        assertNull(ConnectActivity.parseOAuthCallback(uri))
    }

    @Test
    fun errorParamTakesPrecedenceOverToken() {
        val uri = Uri.parse("cuview://oauth/callback?error=some_error&token=pk_abc")
        assertEquals(OAuthResult.Failure("some_error"),
            ConnectActivity.parseOAuthCallback(uri))
    }
}
