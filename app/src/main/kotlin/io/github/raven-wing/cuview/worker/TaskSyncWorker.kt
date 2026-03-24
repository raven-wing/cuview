package io.github.raven_wing.cuview.worker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.raven_wing.cuview.BuildConfig
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.raven_wing.cuview.data.repository.CUViewRepository
import io.github.raven_wing.cuview.data.storage.TaskStorage
import io.github.raven_wing.cuview.widget.CUViewWidget
import io.github.raven_wing.cuview.widget.CUViewWidget.Companion.REFRESH_KEY
import java.util.concurrent.TimeUnit

/**
 * Background worker that fetches tasks from ClickUp and updates the widget.
 *
 * Triggered in three ways:
 * - [enqueuePeriodicSync] — every 15 minutes, network-constrained, scheduled by
 *   [io.github.raven_wing.cuview.ui.config.WidgetConfigActivity] on first save.
 * - [enqueueImmediateSync] — on-demand, no network constraint (widget shows cached tasks
 *   immediately; a stale-data banner appears if the API call then fails offline).
 * - [io.github.raven_wing.cuview.widget.RefreshAction] — user taps ↻ in the widget.
 *
 * On failure the worker retries up to [MAX_RETRIES] times with exponential backoff,
 * then calls [Result.failure] so WorkManager stops retrying until the next periodic window.
 * The widget always shows the last successfully cached tasks with an error banner.
 */
class TaskSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (BuildConfig.DEBUG) Log.d("TaskSyncWorker", "doWork: widgetId=$widgetId attempt=$runAttemptCount")
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()

        val taskStorage = TaskStorage(context, widgetId)

        val repository = CUViewRepository(context)

        // Guard: if the widget has no tasks source configured (e.g. onUpdate fired before the
        // user finished WidgetConfigActivity), silently bail out — do not flip isSyncing
        // or render "Updating…", which would stick if this worker gets cancelled.
        if (!repository.isConfigured(widgetId)) {
            if (BuildConfig.DEBUG) Log.d("TaskSyncWorker", "doWork: skip — widget not configured yet")
            return Result.failure()
        }

        // Only show "Updating…" if there is nothing cached yet — if preview tasks are
        // already stored (from WidgetConfigActivity), skip straight to the sync so the
        // widget keeps showing the preview list instead of flashing "Updating…".
        val cachedTasks = taskStorage.loadTasks()
        if (BuildConfig.DEBUG) Log.d("TaskSyncWorker", "doWork: cached=${cachedTasks.size} tasks before sync")
        if (cachedTasks.isEmpty()) {
            taskStorage.saveSyncStartMs()
            taskStorage.setSyncing(true)
            notifyWidget(widgetId)
        }

        val syncResult = repository.syncTasks(widgetId)

        taskStorage.setSyncing(false)
        notifyWidget(widgetId)

        if (BuildConfig.DEBUG && syncResult.isFailure) {
            Log.e("TaskSyncWorker", "syncTasks failed (attempt $runAttemptCount): ${syncResult.exceptionOrNull()?.message}", syncResult.exceptionOrNull())
        }
        return when {
            syncResult.isSuccess -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    /**
     * Signals Glance to re-run [CUViewWidget.provideGlance] with fresh data.
     *
     * [updateAppWidgetState] writes to Glance's DataStore-backed state, which the Glance
     * session manager monitors. A state change causes Glance to call [CUViewWidget.provideGlance]
     * again, where TaskStorage is re-read. [updateAll] is kept as a fallback for scenarios
     * where the session manager hasn't started yet (e.g. first boot before onUpdate fires).
     */
    private suspend fun notifyWidget(widgetId: Int) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        if (glanceId != null) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[REFRESH_KEY] = (this[REFRESH_KEY] ?: 0) + 1
                }
            }
        }
        CUViewWidget().updateAll(context)
    }

    companion object {
        private const val KEY_WIDGET_ID = "widget_id"
        private const val MAX_RETRIES = 3

        fun enqueuePeriodicSync(context: Context, widgetId: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TaskSyncWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_WIDGET_ID to widgetId))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                periodicWorkName(widgetId),
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueImmediateSync(context: Context, widgetId: Int) {
            // No network constraint: run immediately so the widget renders pre-populated
            // tasks without waiting for connectivity. If the real API call fails due to
            // lack of connectivity, the worker retries and shows a stale-data banner.
            val request = OneTimeWorkRequestBuilder<TaskSyncWorker>()
                .setInputData(workDataOf(KEY_WIDGET_ID to widgetId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                immediateWorkName(widgetId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelPeriodicSync(context: Context, widgetId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(periodicWorkName(widgetId))
        }

        private fun periodicWorkName(widgetId: Int) = "cuview_periodic_sync_$widgetId"
        private fun immediateWorkName(widgetId: Int) = "cuview_immediate_sync_$widgetId"
    }
}
