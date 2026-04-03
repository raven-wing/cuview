package io.github.raven_wing.cuview.ui.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric tests for WidgetConfigActivity's OAuth callback handling.
//
// OAuth lives entirely in WidgetConfigActivity — these tests verify that onNewIntent routes
// tokens and errors correctly, ignores unrelated URIs, and rejects callbacks that
// don't carry the state nonce we generated (CSRF protection).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetConfigActivityOAuthTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        WidgetConfigActivity.ioDispatcher = UnconfinedTestDispatcher()
    }

    @After
    fun tearDown() {
        WidgetConfigActivity.ioDispatcher = Dispatchers.IO
    }

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
    private fun seedState(activity: WidgetConfigActivity, state: String) {
        activity.getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_STATE, Context.MODE_PRIVATE)
            .edit().putString(WidgetConfigActivity.KEY_PENDING_STATE, state).apply()
    }

    private fun launchActivity(): ActivityScenario<WidgetConfigActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
        }
        return ActivityScenario.launch(intent)
    }

    @Test
    fun onNewIntent_successToken_setsPendingOAuthResult() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "state-abc")
                activity.onNewIntent(oauthCallbackIntent(token = "pk_abc123", state = "state-abc"))
            }
            scenario.onActivity { activity ->
                assertEquals(OAuthResult.Success("pk_abc123"), activity.pendingOAuthResult)
            }
        }
    }

    @Test
    fun onNewIntent_error_setsPendingOAuthResult() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "state-xyz")
                activity.onNewIntent(oauthCallbackIntent(error = "token_exchange_failed", state = "state-xyz"))
            }
            scenario.onActivity { activity ->
                assertEquals(OAuthResult.Failure("token_exchange_failed"), activity.pendingOAuthResult)
            }
        }
    }

    @Test
    fun onNewIntent_unrelatedUri_doesNotChangePendingResult() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                activity.onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthResult)
            }
        }
    }

    // Regression: M-2 — a malicious app can fire cuview://oauth/callback with an
    // attacker-controlled token. Without state validation this would inject the token.
    @Test
    fun onNewIntent_missingState_isRejected() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "expected-state")
                // Callback arrives without the state parameter
                activity.onNewIntent(oauthCallbackIntent(token = "pk_injected"))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthResult)
            }
        }
    }

    @Test
    fun onNewIntent_wrongState_isRejected() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "expected-state")
                activity.onNewIntent(oauthCallbackIntent(token = "pk_injected", state = "attacker-state"))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthResult)
            }
        }
    }

    @Test
    fun onNewIntent_noStoredState_isRejected() {
        // No seedState call — simulates a callback arriving without an OAuth flow in flight
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                activity.onNewIntent(oauthCallbackIntent(token = "pk_abc123", state = "any-state"))
            }
            scenario.onActivity { activity ->
                assertNull(activity.pendingOAuthResult)
            }
        }
    }

    @Test
    fun onNewIntent_stateIsConsumedAfterUse_replayIsRejected() {
        // The state nonce must be single-use: a second identical callback must be rejected.
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                seedState(activity, "one-time-state")
                activity.onNewIntent(oauthCallbackIntent(token = "pk_first", state = "one-time-state"))
            }
            scenario.onActivity { activity ->
                assertEquals(OAuthResult.Success("pk_first"), activity.pendingOAuthResult)
                // Replay the same callback — state is already consumed, so it must be rejected
                activity.onNewIntent(oauthCallbackIntent(token = "pk_replay", state = "one-time-state"))
            }
            scenario.onActivity { activity ->
                // pendingOAuthResult must not have been updated to pk_replay
                assertEquals(OAuthResult.Success("pk_first"), activity.pendingOAuthResult)
            }
        }
    }
}
