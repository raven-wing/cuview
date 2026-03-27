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
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.TasksSource
import io.github.raven_wing.cuview.data.repository.CUViewRepository
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import io.github.raven_wing.cuview.widget.CUViewWidget
import io.github.raven_wing.cuview.widget.WidgetTheme
import io.github.raven_wing.cuview.worker.TaskSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuration screen shown by Android when the user places the widget on the home screen.
 *
 * Lifecycle contract:
 * - [setResult] with [RESULT_CANCELED] **must** be the first call in [onCreate]. If the user
 *   presses back, Android uses this result to remove the pending widget slot. Omitting it
 *   causes a broken empty widget to appear on the home screen.
 * - [setResult] with [RESULT_OK] is called in [onConfigSaved] after the user selects a tasks
 *   source and taps Save. This signals Android to finalize widget placement.
 *
 * [apiToken] and [isCheckingToken] are updated in [onResume] so the config screen reflects
 * the current token state each time the user returns from
 * [io.github.raven_wing.cuview.ui.connect.ConnectActivity] after connecting their ClickUp account.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var apiToken by mutableStateOf<String?>(null)
    private var isCheckingToken by mutableStateOf(true)

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
                            apiToken = apiToken,
                            isCheckingToken = isCheckingToken,
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
            apiToken = withContext(Dispatchers.IO) { SecurePreferences(this@WidgetConfigActivity).apiToken }
            isCheckingToken = false
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
}

