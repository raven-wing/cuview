package io.github.raven_wing.cuview.ui.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Regression tests for the "fresh instance" OAuth callback path.
//
// Background: WidgetConfigActivity is started by the home screen launcher via
// startActivityForResult. This places it in the *launcher's* task, not in
// a task with the app's own affinity. When the Cloudflare Worker fires an
// intent:// callback with FLAG_SINGLE_TOP, Android cannot deliver it to the
// existing instance via onNewIntent because there is no matching activity at
// the top of the app's task. Instead, a *new* WidgetConfigActivity instance
// is created in a new task. That fresh instance must:
//   1. Detect the cuview:// URI, handle it, and finish() immediately.
//   2. Save any OAuth error in PREFS_OAUTH_PENDING_ERROR for the original
//      instance to read in onResume.
//   3. Call moveTaskToFront with the task ID stored by the original instance
//      so Android brings that instance back to the foreground.
//
// The original instance must:
//   4. Store its task ID in PREFS_WIDGET_CONFIG_SESSION so the fresh instance
//      can retrieve it for moveTaskToFront.
//   5. Read PREFS_OAUTH_PENDING_ERROR in onResume and propagate the error to
//      pendingOAuthResult.
//
// Note: tests use the error callback path where SecurePreferences is not
// touched. The success path saves the token via EncryptedSharedPreferences,
// which Robolectric does not support (see SecurePreferencesTokenTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetConfigActivityFreshInstanceCallbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun seedState(state: String) {
        context.getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_STATE, Context.MODE_PRIVATE)
            .edit().putString(WidgetConfigActivity.KEY_PENDING_STATE, state).apply()
    }

    /** Build a cuview://oauth/callback intent routed directly to WidgetConfigActivity. */
    private fun callbackIntent(
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
            .apply { setClass(context, WidgetConfigActivity::class.java) }
    }

    private fun launchNormally(widgetId: Int = 42): ActivityScenario<WidgetConfigActivity> {
        val intent = Intent(context, WidgetConfigActivity::class.java)
            .apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }
        return ActivityScenario.launch(intent)
    }

    // ── 1. Fresh instance finishes immediately ─────────────────────────────────

    // Regression: before the fix, the fresh instance had no appWidgetId and would
    // call finish() in the appWidgetId-check branch. That still worked by accident,
    // but the OAuth error was never saved and moveTaskToFront was never called.
    // After the fix, the cuview:// scheme is detected *before* the appWidgetId check,
    // so token/error storage and moveTaskToFront happen first, then the activity finishes.
    @Test
    fun freshInstance_withErrorCallback_finishesImmediately() {
        seedState("state-xyz")
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "token_exchange_failed", state = "state-xyz"),
        ).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun freshInstance_withUnknownUri_finishesImmediatelyWithoutStoringError() {
        // An unrecognised cuview:// URI (no matching state) still finishes without side effects.
        seedState("state-abc")
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "some_error", state = "wrong-state"),
        ).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
        val stored = context
            .getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_PENDING_ERROR, Context.MODE_PRIVATE)
            .getString(WidgetConfigActivity.KEY_PENDING_ERROR, null)
        assertNull(stored)
    }

    // ── 2. Error is stored in pending prefs ────────────────────────────────────

    @Test
    fun freshInstance_errorCallback_storesErrorInPendingPrefs() {
        seedState("state-xyz")
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "token_exchange_failed", state = "state-xyz"),
        ).use { /* finishes immediately */ }

        val stored = context
            .getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_PENDING_ERROR, Context.MODE_PRIVATE)
            .getString(WidgetConfigActivity.KEY_PENDING_ERROR, null)
        assertEquals("token_exchange_failed", stored)
    }

    // ── 3. State validation on the fresh-instance path ─────────────────────────

    @Test
    fun freshInstance_wrongState_doesNotStoreError() {
        seedState("expected-state")
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "some_error", state = "attacker-state"),
        ).use { /* finishes immediately */ }

        val stored = context
            .getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_PENDING_ERROR, Context.MODE_PRIVATE)
            .getString(WidgetConfigActivity.KEY_PENDING_ERROR, null)
        assertNull(stored)
    }

    @Test
    fun freshInstance_missingStateParam_doesNotStoreError() {
        seedState("expected-state")
        // Callback arrives without a state parameter — should be silently rejected.
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "some_error"), // no state
        ).use { /* finishes immediately */ }

        val stored = context
            .getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_PENDING_ERROR, Context.MODE_PRIVATE)
            .getString(WidgetConfigActivity.KEY_PENDING_ERROR, null)
        assertNull(stored)
    }

    @Test
    fun freshInstance_noStoredState_doesNotStoreError() {
        // No seedState — simulates a callback arriving with no OAuth flow in flight.
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "some_error", state = "any-state"),
        ).use { /* finishes immediately */ }

        val stored = context
            .getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_PENDING_ERROR, Context.MODE_PRIVATE)
            .getString(WidgetConfigActivity.KEY_PENDING_ERROR, null)
        assertNull(stored)
    }

    // ── 4. Original instance stores its task ID ────────────────────────────────

    // The original WidgetConfigActivity must persist its task ID so the fresh-instance
    // callback can call moveTaskToFront with it after saving the token/error.
    @Test
    fun normalLaunch_storesTaskIdInSessionPrefs() {
        launchNormally().use { scenario ->
            scenario.onActivity { _ ->
                val taskId = context
                    .getSharedPreferences(WidgetConfigActivity.PREFS_WIDGET_CONFIG_SESSION, Context.MODE_PRIVATE)
                    .getInt(WidgetConfigActivity.KEY_WIDGET_CONFIG_TASK_ID, -1)
                assertNotEquals(-1, taskId)
            }
        }
    }

    // ── 5. Pending error prefs contract ────────────────────────────────────────
    //
    // onResume reads PREFS_OAUTH_PENDING_ERROR, sets pendingOAuthResult, and clears the
    // prefs. That path cannot be exercised end-to-end in Robolectric because onResume's
    // first coroutine step calls SecurePreferences(context), which uses
    // EncryptedSharedPreferences — unsupported in Robolectric (see SecurePreferencesTokenTest).
    // The tests below verify the two independent halves of the contract:
    //   a. the fresh-instance callback writes to the right prefs key (already covered above),
    //   b. the prefs key is present after a fresh-instance error callback so that onResume
    //      can read it on a real device.

    @Test
    fun freshInstance_errorCallback_pendingErrorKeyMatchesWhatOnResumeReads() {
        // Verify that handleOAuthCallback writes exactly to the prefs file and key that
        // onResume reads, so the two sides of the handshake are in sync.
        seedState("state-xyz")
        ActivityScenario.launch<WidgetConfigActivity>(
            callbackIntent(error = "token_exchange_failed", state = "state-xyz"),
        ).use { /* finishes immediately */ }

        val prefs = context.getSharedPreferences(
            WidgetConfigActivity.PREFS_OAUTH_PENDING_ERROR, Context.MODE_PRIVATE,
        )
        // Key must exist and hold the error string.
        assertEquals(
            "token_exchange_failed",
            prefs.getString(WidgetConfigActivity.KEY_PENDING_ERROR, null),
        )
        // No other keys should be present — avoids accidental key name drift.
        assertEquals(setOf(WidgetConfigActivity.KEY_PENDING_ERROR), prefs.all.keys)
    }
}
