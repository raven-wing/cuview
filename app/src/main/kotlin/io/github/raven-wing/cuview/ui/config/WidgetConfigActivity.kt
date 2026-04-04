package io.github.raven_wing.cuview.ui.config

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import io.github.raven_wing.cuview.BuildConfig
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.TasksSource
import io.github.raven_wing.cuview.data.repository.CUViewRepository
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import io.github.raven_wing.cuview.widget.CUViewWidget
import io.github.raven_wing.cuview.widget.WidgetTheme
import io.github.raven_wing.cuview.worker.TaskSyncWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── OAuth result ───────────────────────────────────────────────────────────────

/** Outcome of a ClickUp OAuth flow, parsed from the `cuview://oauth/callback` redirect URI. */
sealed class OAuthResult {
    data class Success(val token: String) : OAuthResult()
    data class Failure(val error: String) : OAuthResult()
}

// ── Token state ────────────────────────────────────────────────────────────────

sealed class TokenState {
    data object Loading : TokenState()
    data object None : TokenState()
    data class Token(val value: String) : TokenState()
}

// ── Activity ───────────────────────────────────────────────────────────────────

/**
 * Configuration screen shown by Android when the user places the widget on the home screen.
 *
 * Also handles the OAuth connect/disconnect flow and receives the OAuth callback.
 *
 * Lifecycle contract:
 * - [setResult] with [RESULT_CANCELED] **must** be the first call in [onCreate]. If the user
 *   presses back, Android uses this result to remove the pending widget slot. Omitting it
 *   causes a broken empty widget to appear on the home screen.
 * - [setResult] with [RESULT_OK] is called in [onConfigSaved] after the user selects a tasks
 *   source and taps Save. This signals Android to finalize widget placement.
 *
 * OAuth callback routing — two paths:
 * - **singleTop** (happy path): if the activity is already at the top of a task with the
 *   app's affinity when the callback fires, Android calls [onNewIntent] → [handleOAuthIntent]
 *   saves the token and sets [pendingOAuthResult].
 * - **fresh instance** (task-affinity mismatch): widget config activities are started by the
 *   home screen launcher via startActivityForResult, landing them in the launcher's task rather
 *   than a task with the app's own affinity. When the singleTop routing therefore creates a new
 *   instance instead of calling onNewIntent, [onCreate] detects the cuview:// URI, calls
 *   [handleOAuthCallback] to save the token, then finishes immediately. The original instance
 *   picks up the token in [onResume] (which already reads SecurePreferences). OAuth errors are
 *   stored in [PREFS_OAUTH_PENDING_ERROR] and read from [onResume] as well.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var tokenState by mutableStateOf<TokenState>(TokenState.Loading)
    var pendingOAuthResult by mutableStateOf<OAuthResult?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CRITICAL: set RESULT_CANCELED first so back press doesn't add a broken widget.
        setResult(RESULT_CANCELED)

        // OAuth callback may arrive as a fresh instance (see class-level kdoc).
        // Handle it before the appWidgetId check so the token is saved before we finish.
        if (intent?.data?.scheme == "cuview") {
            handleOAuthCallback(intent)
            finish()
            return
        }

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Store task ID so the OAuth callback instance can bring this task back to front.
        getSharedPreferences(PREFS_WIDGET_CONFIG_SESSION, MODE_PRIVATE)
            .edit().putInt(KEY_WIDGET_CONFIG_TASK_ID, taskId).apply()

        val widgetIdCapture = appWidgetId
        lifecycleScope.launch {
            val repository: CUViewRepository
            val initialTasksSource: TasksSource?
            val initialTasks: List<CUTask>?
            val initialThemeId: String?
            withContext(Dispatchers.IO) {
                val securePrefs = SecurePreferences(this@WidgetConfigActivity)
                val taskStorage = TaskStorage(this@WidgetConfigActivity, widgetIdCapture)
                initialTasksSource = taskStorage.loadTasksSource()
                initialTasks = if (initialTasksSource != null) taskStorage.loadTasks() else null
                initialThemeId = taskStorage.loadThemeId()
                repository = CUViewRepository(this@WidgetConfigActivity, securePrefs)
            }

            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ConfigScreen(
                            tokenState = tokenState,
                            pendingOAuthResult = pendingOAuthResult,
                            onOAuthResultConsumed = { pendingOAuthResult = null },
                            onTokenChanged = { tokenState = it },
                            initialThemeId = initialThemeId,
                            initialTasksSource = initialTasksSource,
                            initialTasks = initialTasks,
                            onSave = ::onConfigSaved,
                            callbacks = RepositoryCallbacks(
                                fetchSpaces = repository::fetchSpaces,
                                fetchSpaceContents = repository::fetchSpaceContents,
                                fetchFolderViews = repository::fetchFolderViews,
                                fetchListViews = repository::fetchListViews,
                                previewTasksSource = repository::previewTasks,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) { SecurePreferences(this@WidgetConfigActivity).apiToken }
            tokenState = if (token != null) TokenState.Token(token) else TokenState.None
            // Pick up an OAuth error stored by a fresh-instance callback that ran in a separate task.
            val pendingError = withContext(Dispatchers.IO) {
                getSharedPreferences(PREFS_OAUTH_PENDING_ERROR, MODE_PRIVATE)
                    .getString(KEY_PENDING_ERROR, null)
                    ?.also { getSharedPreferences(PREFS_OAUTH_PENDING_ERROR, MODE_PRIVATE).edit().clear().apply() }
            }
            if (pendingError != null) pendingOAuthResult = OAuthResult.Failure(pendingError)
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    /** Called via [onNewIntent] when singleTop routing delivers the callback to the existing instance. */
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
        // Save token on IO and await before updating pendingOAuthResult, so the token is
        // persisted even if the process is killed between the save and the UI update.
        lifecycleScope.launch {
            if (result is OAuthResult.Success) {
                var storageOk = false
                withContext(ioDispatcher) {
                    try {
                        SecurePreferences(this@WidgetConfigActivity).apiToken = result.token.trim()
                        storageOk = true
                    } catch (_: Exception) {
                        // Keystore unavailable — surface the failure so the UI does not show
                        // a false "connected" state while the token is actually lost.
                    }
                }
                if (!storageOk) {
                    pendingOAuthResult = OAuthResult.Failure("token_storage_failed")
                    return@launch
                }
            }
            pendingOAuthResult = result
        }
    }

    /**
     * Called from [onCreate] when a fresh instance is started by the OAuth callback.
     * Saves the token (or error) and returns — [finish] is called immediately after.
     * The original instance picks up the result in [onResume].
     */
    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        val oauthStatePrefs = getSharedPreferences(PREFS_OAUTH_STATE, MODE_PRIVATE)
        val expectedState = oauthStatePrefs.getString(KEY_PENDING_STATE, null)
        oauthStatePrefs.edit().remove(KEY_PENDING_STATE).apply()
        if (expectedState == null || expectedState != uri.getQueryParameter("state")) return
        when (val result = parseOAuthCallback(uri) ?: return) {
            is OAuthResult.Success -> SecurePreferences(this).apiToken = result.token.trim()
            is OAuthResult.Failure -> getSharedPreferences(PREFS_OAUTH_PENDING_ERROR, MODE_PRIVATE)
                .edit().putString(KEY_PENDING_ERROR, result.error).apply()
        }
        // Bring the WidgetConfigActivity task (sitting in the launcher's task behind Chrome)
        // to the foreground so its onResume fires and picks up the saved token.
        // This instance was just started by Chrome (a foreground app), so it temporarily has
        // foreground-launch privilege needed to call moveTaskToFront on API 29+.
        val wcaTaskId = getSharedPreferences(PREFS_WIDGET_CONFIG_SESSION, MODE_PRIVATE)
            .getInt(KEY_WIDGET_CONFIG_TASK_ID, -1)
        if (wcaTaskId != -1) {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).moveTaskToFront(wcaTaskId, 0)
        }
    }

    private fun onConfigSaved(
        tasksSource: TasksSource,
        previewTasks: List<CUTask>,
        theme: WidgetTheme,
    ) {
        if (BuildConfig.DEBUG) Log.d("WCA", "onConfigSaved: widgetId=$appWidgetId tasks=${previewTasks.size} theme=${theme.id}")
        val taskStorage = TaskStorage(this, appWidgetId)
        taskStorage.saveTasksSource(tasksSource)
        taskStorage.saveTasks(previewTasks)
        taskStorage.saveThemeId(theme.id)

        val widgetIdCapture = appWidgetId
        lifecycleScope.launch {
            GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetIdCapture)?.let { glanceId ->
                updateAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[CUViewWidget.LAST_SYNCED_KEY] = System.currentTimeMillis() }
                }
                CUViewWidget().update(this@WidgetConfigActivity, glanceId)
            }
            TaskSyncWorker.enqueueImmediateSync(this@WidgetConfigActivity, widgetIdCapture)
            TaskSyncWorker.enqueuePeriodicSync(this@WidgetConfigActivity, widgetIdCapture)
            setResult(RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetIdCapture) })
            if (BuildConfig.DEBUG) Log.d("WCA", "onConfigSaved: setResult(RESULT_OK) widgetId=$widgetIdCapture")
            finish()
        }
    }

    companion object {
        // Overridable in tests to avoid real IO thread dispatch (eliminates Thread.sleep).
        @JvmField
        var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        internal const val PREFS_OAUTH_STATE = "cuview_oauth_state"
        internal const val KEY_PENDING_STATE = "pending_state"
        internal const val PREFS_OAUTH_PENDING_ERROR = "cuview_oauth_pending_error"
        internal const val KEY_PENDING_ERROR = "pending_error"
        internal const val PREFS_WIDGET_CONFIG_SESSION = "cuview_widget_config_session"
        internal const val KEY_WIDGET_CONFIG_TASK_ID = "widget_config_task_id"

        fun parseOAuthCallback(uri: Uri): OAuthResult? {
            if (uri.scheme != "cuview" || uri.host != "oauth") return null
            val error = uri.getQueryParameter("error")
            if (error != null) return OAuthResult.Failure(error)
            val token = uri.getQueryParameter("token") ?: return null
            return OAuthResult.Success(token)
        }
    }
}
