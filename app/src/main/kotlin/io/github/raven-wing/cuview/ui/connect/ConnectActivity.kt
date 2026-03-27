package io.github.raven_wing.cuview.ui.connect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.raven_wing.cuview.BuildConfig
import io.github.raven_wing.cuview.R
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ── OAuth result ───────────────────────────────────────────────────────────────

/** Outcome of a ClickUp OAuth flow, parsed from the `cuview://oauth/callback` redirect URI. */
sealed class OAuthResult {
    data class Success(val token: String) : OAuthResult()
    data class Failure(val error: String) : OAuthResult()
}

// ── Activity ───────────────────────────────────────────────────────────────────

/**
 * Activity that handles OAuth connect/disconnect and receives the OAuth callback.
 *
 * The OAuth flow:
 * 1. User taps "Connect Workspace" → a Chrome Custom Tab opens the ClickUp auth page.
 * 2. After authorization, ClickUp redirects to the Cloudflare Worker, which exchanges the
 *    code for a token and fires `cuview://oauth/callback?token=…&state=…`.
 * 3. Android routes the intent to this activity via [onNewIntent] (launchMode=singleTop).
 * 4. [handleOAuthIntent] validates the CSRF state token and stores the API token.
 *
 * The Chrome Custom Tab is launched with [Intent.FLAG_ACTIVITY_NEW_TASK] so it opens in
 * Chrome's own task. When the callback arrives, Android finds this activity at the top of
 * the app's task and delivers it via [onNewIntent]. Without that flag, Chrome would share
 * this task and SINGLE_TOP matching would fail.
 */
class ConnectActivity : ComponentActivity() {

    var pendingOAuthToken by mutableStateOf<OAuthResult?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { handleOAuthIntent(it) }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConnectScreen(
                        pendingOAuthResult = pendingOAuthToken,
                        onOAuthResultConsumed = { pendingOAuthToken = null },
                    )
                }
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent) {
        val uri = intent.data ?: return

        // Validate the OAuth state parameter to prevent CSRF / token injection.
        // A co-installed malicious app could fire a cuview://oauth/callback intent at
        // any time; requiring that the state matches the one we generated (and stored
        // before launching the CCT) ensures we only accept callbacks we initiated.
        val oauthStatePrefs = getSharedPreferences(PREFS_OAUTH_STATE, MODE_PRIVATE)
        val expectedState = oauthStatePrefs.getString(KEY_PENDING_STATE, null)
        oauthStatePrefs.edit().remove(KEY_PENDING_STATE).apply() // consume regardless of outcome
        if (expectedState == null || expectedState != uri.getQueryParameter("state")) return

        val result = parseOAuthCallback(uri) ?: return
        pendingOAuthToken = result
    }

    companion object {
        internal const val PREFS_OAUTH_STATE = "cuview_oauth_state"
        internal const val KEY_PENDING_STATE = "pending_state"

        fun parseOAuthCallback(uri: Uri): OAuthResult? {
            if (uri.scheme != "cuview" || uri.host != "oauth") return null
            val error = uri.getQueryParameter("error")
            if (error != null) return OAuthResult.Failure(error)
            val token = uri.getQueryParameter("token") ?: return null
            return OAuthResult.Success(token)
        }
    }
}

// ── Connect screen ─────────────────────────────────────────────────────────────

@Composable
private fun ConnectScreen(
    pendingOAuthResult: OAuthResult?,
    onOAuthResultConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(false) }
    var isCheckingToken by remember { mutableStateOf(true) }
    var oauthError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = withContext(Dispatchers.IO) { SecurePreferences(context).apiToken }
        isConnected = token != null
        isCheckingToken = false
    }

    LaunchedEffect(pendingOAuthResult) {
        val result = pendingOAuthResult ?: return@LaunchedEffect
        // NOTE: onOAuthResultConsumed() must be called AFTER all state updates and IO.
        // Calling it first sets pendingOAuthToken = null, scheduling a recomposition that
        // changes the LaunchedEffect key. If any IO (withContext) follows, Compose cancels
        // this coroutine on the next frame before the IO completes — isConnected = true
        // is never reached and the token is never saved.
        when (result) {
            is OAuthResult.Success -> {
                withContext(Dispatchers.IO) { SecurePreferences(context).apiToken = result.token }
                isConnected = true
                oauthError = null
            }
            is OAuthResult.Failure -> oauthError = result.error
        }
        onOAuthResultConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(32.dp))

        ConnectSection(
            isConnected = isConnected,
            isCheckingToken = isCheckingToken,
            onConnect = {
                if (BuildConfig.USE_MOCK_API) {
                    scope.launch {
                        withContext(Dispatchers.IO) { SecurePreferences(context).apiToken = "mock_token" }
                        isConnected = true
                    }
                } else {
                    val state = UUID.randomUUID().toString()
                    // Persist state before launching the CCT so handleOAuthIntent can
                    // validate it even if the process is recreated between launch and callback.
                    context.getSharedPreferences(ConnectActivity.PREFS_OAUTH_STATE, Context.MODE_PRIVATE)
                        .edit().putString(ConnectActivity.KEY_PENDING_STATE, state).apply()
                    val redirectUri = Uri.encode(BuildConfig.CLOUDFLARE_WORKER_URL)
                    val authUrl = "https://app.clickup.com/api" +
                        "?client_id=${BuildConfig.CLICKUP_CLIENT_ID}" +
                        "&redirect_uri=$redirectUri" +
                        "&state=$state"
                    // FLAG_ACTIVITY_NEW_TASK opens Chrome in its own task, keeping
                    // ConnectActivity alone at the top of the io.github.raven_wing.cuview task.
                    // When the Cloudflare Worker fires the intent:// callback with
                    // launchFlags=NEW_TASK|SINGLE_TOP, Android finds this task, sees
                    // ConnectActivity at the top, and routes the callback via onNewIntent.
                    // Without this flag, ChromeCCT would share ConnectActivity's task, and
                    // SINGLE_TOP would never match (ChromeCCT is on top, not ConnectActivity).
                    val cct = CustomTabsIntent.Builder().build()
                    cct.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    cct.launchUrl(context, Uri.parse(authUrl))
                }
            },
            onDisconnect = {
                scope.launch(Dispatchers.IO) { SecurePreferences(context).apiToken = null }
                isConnected = false
            },
        )

        if (oauthError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = oauthError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (isConnected) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.main_widget_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Connect / disconnect section ───────────────────────────────────────────────

@Composable
private fun ConnectSection(
    isConnected: Boolean,
    isCheckingToken: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (!isConnected) {
        Button(
            onClick = onConnect,
            enabled = !isCheckingToken,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isCheckingToken) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.config_connect_button))
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.config_connected_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.config_disconnect_button),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onDisconnect)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}
