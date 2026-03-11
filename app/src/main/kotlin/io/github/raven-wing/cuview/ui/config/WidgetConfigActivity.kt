package io.github.raven_wing.cuview.ui.config

import android.appwidget.AppWidgetManager
import android.content.Intent
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
import io.github.raven_wing.cuview.data.model.Task
import io.github.raven_wing.cuview.data.repository.CUViewRepository
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import io.github.raven_wing.cuview.widget.CUViewWidget
import io.github.raven_wing.cuview.widget.WidgetTheme
import io.github.raven_wing.cuview.worker.TaskSyncWorker
import kotlinx.coroutines.launch

/**
 * Configuration screen shown by Android when the user places the widget on the home screen.
 *
 * Lifecycle contract:
 * - [setResult] with [RESULT_CANCELED] **must** be the first call in [onCreate]. If the user
 *   presses back, Android uses this result to remove the pending widget slot. Omitting it
 *   causes a broken empty widget to appear on the home screen.
 * - [setResult] with [RESULT_OK] is called in [onConfigSaved] after the user selects a target
 *   and taps Save. This signals Android to finalize widget placement.
 *
 * [tokenCheckSignal] is incremented in [onResume] so the config screen re-reads the token
 * each time the user returns from [io.github.raven_wing.cuview.ui.main.MainActivity]
 * after connecting their ClickUp account.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val repository by lazy { CUViewRepository(this) }
    private var tokenCheckSignal by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CRITICAL: set RESULT_CANCELED first so back press doesn't add a broken widget.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val taskStorage = TaskStorage(this, appWidgetId)
        val securePrefs = SecurePreferences(this)
        val savedTargetId = securePrefs.viewId(appWidgetId)
        val savedLabel = taskStorage.loadTargetName()
        val initialTarget = if (savedTargetId != null && savedLabel != null) {
            SelectedTarget(
                id = savedTargetId,
                label = savedLabel,
                isListTarget = securePrefs.isListTarget(appWidgetId),
            )
        } else null
        val initialTasks = if (initialTarget != null) taskStorage.loadTasks() else null

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(
                        tokenCheckSignal = tokenCheckSignal,
                        initialThemeId = taskStorage.loadThemeId(),
                        initialTarget = initialTarget,
                        initialTasks = initialTasks,
                        onSave = ::onConfigSaved,
                        callbacks = RepositoryCallbacks(
                            fetchSpaces = repository::fetchSpaces,
                            fetchSpaceContents = repository::fetchSpaceContents,
                            fetchFolderViews = repository::fetchFolderViews,
                            fetchListViews = repository::fetchListViews,
                            previewTarget = repository::previewTasks,
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tokenCheckSignal++
    }

    private fun onConfigSaved(
        targetId: String,
        isListTarget: Boolean,
        targetLabel: String,
        previewTasks: List<Task>,
        theme: WidgetTheme,
    ) {
        if (BuildConfig.DEBUG) Log.d("WCA", "onConfigSaved: widgetId=$appWidgetId tasks=${previewTasks.size} theme=${theme.id}")
        val prefs = SecurePreferences(this)
        prefs.setTarget(appWidgetId, targetId, isListTarget)

        val taskStorage = TaskStorage(this, appWidgetId)
        taskStorage.saveTargetName(targetLabel)
        taskStorage.saveTasks(previewTasks)
        taskStorage.saveThemeId(theme.id)

        // Trigger Glance to re-run provideGlance() by bumping the DataStore-backed state.
        // updateAppWidgetState() is the correct Glance mechanism: it signals the session
        // manager via a DataStore change, causing provideGlance() to run with fresh TaskStorage
        // data (including the preview tasks saved above). The lifecycleScope coroutine completes
        // well before onDestroy() cancels it (~50 ms, activity teardown takes longer).
        val widgetIdCapture = appWidgetId
        lifecycleScope.launch {
            GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetIdCapture)?.let { glanceId ->
                updateAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[CUViewWidget.REFRESH_KEY] = (this[CUViewWidget.REFRESH_KEY] ?: 0) + 1 }
                }
            }
        }

        TaskSyncWorker.enqueueImmediateSync(this, appWidgetId)
        TaskSyncWorker.enqueuePeriodicSync(this, appWidgetId)

        val resultIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultIntent)
        if (BuildConfig.DEBUG) Log.d("WCA", "onConfigSaved: setResult(RESULT_OK) widgetId=$appWidgetId")
        finish()
    }
}
