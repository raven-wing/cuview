package io.github.raven_wing.cuview.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric tests for MainActivity's OAuth callback handling.
//
// In the new architecture, OAuth lives entirely in MainActivity — WidgetConfigActivity
// is a lightweight target-selector only. These tests verify that onNewIntent routes
// tokens and errors correctly, ignores unrelated URIs, and rejects callbacks that
// don't carry the state nonce we generated (CSRF protection).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityOAuthTest {

    private fun oauthCallbackIntent(
        token: String? = null,
        error: String? = null,
        state: String? = null,
    ): Intent {
        val params = buildList {
            if (token != null) add("token=${Uri.encode(token)}")
            if (error != null) add("error=$error")
            if (state != null) add("state=$state")
        }.joinToString("&")
        return Intent(Intent.ACTION_VIEW, Uri.parse("cuview://oauth/callback?$params"))
    }

    /** Pre-populate the pending OAuth state so handleOAuthIntent accepts the callback. */
    private fun seedState(activity: MainActivity, state: String) {
        activity.getSharedPreferences(MainActivity.PREFS_OAUTH_STATE, Context.MODE_PRIVATE)
            .edit().putString(MainActivity.KEY_PENDING_STATE, state).apply()
    }

    @Test
    fun onNewIntent_successToken_setsPendingOAuthToken() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "state-abc")
                activity.onNewIntent(oauthCallbackIntent(token = "pk_abc123", state = "state-abc"))
            }
            scenario.onActivity { activity ->
                assertEquals(OAuthResult.Success("pk_abc123"), activity.pendingOAuthToken)
            }
        }
    }

    @Test
    fun onNewIntent_error_setsPendingOAuthToken() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "state-xyz")
                activity.onNewIntent(oauthCallbackIntent(error = "token_exchange_failed", state = "state-xyz"))
            }
            scenario.onActivity { activity ->
                assertEquals(OAuthResult.Failure("token_exchange_failed"), activity.pendingOAuthToken)
            }
        }
    }

    @Test
    fun onNewIntent_unrelatedUri_doesNotChangePendingToken() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthToken)
            }
        }
    }

    // Regression: M-2 — a malicious app can fire cuview://oauth/callback with an
    // attacker-controlled token. Without state validation this would inject the token.
    @Test
    fun onNewIntent_missingState_isRejected() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "expected-state")
                // Callback arrives without the state parameter
                activity.onNewIntent(oauthCallbackIntent(token = "pk_injected"))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthToken)
            }
        }
    }

    @Test
    fun onNewIntent_wrongState_isRejected() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "expected-state")
                activity.onNewIntent(oauthCallbackIntent(token = "pk_injected", state = "attacker-state"))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthToken)
            }
        }
    }

    @Test
    fun onNewIntent_noStoredState_isRejected() {
        // No seedState call — simulates a callback arriving without an OAuth flow in flight
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNewIntent(oauthCallbackIntent(token = "pk_abc123", state = "any-state"))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthToken)
            }
        }
    }

    @Test
    fun onNewIntent_stateIsConsumedAfterUse_replayIsRejected() {
        // The state nonce must be single-use: a second identical callback must be rejected.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "one-time-state")
                activity.onNewIntent(oauthCallbackIntent(token = "pk_first", state = "one-time-state"))
            }
            scenario.onActivity { activity ->
                assertEquals(OAuthResult.Success("pk_first"), activity.pendingOAuthToken)
                // Replay the same callback — state is already consumed, so it must be rejected
                activity.onNewIntent(oauthCallbackIntent(token = "pk_replay", state = "one-time-state"))
            }
            scenario.onActivity { activity ->
                // pendingOAuthToken must not have been updated to pk_replay
                assertEquals(OAuthResult.Success("pk_first"), activity.pendingOAuthToken)
            }
        }
    }
}
