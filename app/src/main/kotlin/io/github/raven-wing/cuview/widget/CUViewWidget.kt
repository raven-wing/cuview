package io.github.raven_wing.cuview.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.raven_wing.cuview.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.storage.TaskStorage

/**
 * Glance-based home screen widget that renders the task list from [TaskStorage].
 *
 * Renders one of four states based on cache contents:
 * - **TaskListState** — tasks available; shows up to [MAX_VISIBLE_TASKS] tiles with an
 *   optional [ErrorBanner] if the last sync failed.
 * - **SyncingState** — no cached tasks yet and a sync is in progress.
 * - **EmptyState** — sync succeeded but the list/view has no tasks.
 * - **FullErrorState** — no cached tasks and sync failed; shows the error message.
 *
 * All colors are supplied via [WidgetColors] derived from the per-widget [WidgetTheme]
 * stored in [TaskStorage]. The theme is chosen by the user in [io.github.raven_wing.cuview.ui.config.WidgetConfigActivity].
 *
 * The `isSyncing` flag is guarded against stale state: if it has been set for longer than
 * [SYNC_STALE_THRESHOLD_MS] (WorkManager process was killed mid-sync), it is cleared and
 * cached tasks are shown instead of "Updating…" forever.
 */
class CUViewWidget : GlanceAppWidget() {

    // Setting stateDefinition enables Glance's DataStore-backed state tracking.
    // When updateAppWidgetState() is called from a worker or activity, Glance detects
    // the DataStore change and calls provideGlance() again with fresh data.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (BuildConfig.DEBUG) Log.d("CUViewWidget", "provideGlance: ENTRY id=$id")
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val taskStorage = TaskStorage(context, widgetId)
        // Warm up EncryptedSharedPreferences (lazy init, ~100 ms) before entering
        // the composition thread. Reads inside provideContent {} will be fast in-memory lookups.
        taskStorage.loadTasks()

        // Escape hatch: if the isSyncing flag was set but the WorkManager process was killed
        // before it could clear the flag, the widget would show "Updating…" forever. Clear it
        // here, before provideContent {}, to avoid writing to SharedPreferences inside the
        // composition lambda (a side effect that violates composable purity expectations).
        val isSyncingRaw = taskStorage.isSyncing()
        val syncStartMs = taskStorage.loadSyncStartMs()
        val syncIsStale = isSyncingRaw && syncStartMs > 0 &&
            (System.currentTimeMillis() - syncStartMs) > SYNC_STALE_THRESHOLD_MS
        if (syncIsStale) taskStorage.setSyncing(false)

        val colors = WidgetTheme.fromId(taskStorage.loadThemeId()).colors.toWidgetColors()

        provideContent {
            // currentState() subscribes this composition to Glance's DataStore-backed widget
            // state. When updateAppWidgetState() changes this state (from a worker or the config
            // activity), Glance re-runs this lambda so all TaskStorage reads below are fresh.
            currentState<Preferences>()

            val tasks = taskStorage.loadTasks()
            val error = taskStorage.loadError()
            val isSyncing = taskStorage.isSyncing()
            val tasksSourceName = taskStorage.loadTasksSourceName()

            if (BuildConfig.DEBUG) Log.d("CUViewWidget", "compose: widgetId=$widgetId tasks=${tasks.size} error=$error isSyncing=$isSyncing")

            GlanceTheme {
                WidgetContent(tasks = tasks, error = error, isSyncing = isSyncing, tasksSourceName = tasksSourceName, colors = colors)
            }
        }
    }

    @Composable
    private fun WidgetContent(tasks: List<CUTask>, error: String?, isSyncing: Boolean, tasksSourceName: String?, colors: WidgetColors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.background)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            when {
                isSyncing && tasks.isEmpty() -> SyncingState(colors)
                tasks.isEmpty() && error != null -> FullErrorState(error, colors)
                tasks.isEmpty() -> EmptyState(colors)
                else -> TaskListState(tasks = tasks, error = error, tasksSourceName = tasksSourceName, colors = colors)
            }
        }
    }

    @Composable
    private fun TaskListState(tasks: List<CUTask>, error: String?, tasksSourceName: String?, colors: WidgetColors) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(colors)
            if (tasksSourceName != null) {
                Text(
                    text = tasksSourceName,
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                    ),
                    modifier = GlanceModifier.padding(top = 1.dp, bottom = 1.dp),
                )
            }
            Spacer(modifier = GlanceModifier.height(5.dp))

            if (error != null) {
                ErrorBanner(error, colors)
                Spacer(modifier = GlanceModifier.height(5.dp))
            }

            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks.take(MAX_VISIBLE_TASKS)) { task ->
                    TaskTile(task, colors)
                }
            }
        }
    }

    @Composable
    private fun Header(colors: WidgetColors) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Two-tone brand title
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CU",
                    style = TextStyle(
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                )
                Text(
                    text = " View",
                    style = TextStyle(
                        color = colors.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                )
            }
            // Refresh pill
            Box(
                modifier = GlanceModifier
                    .background(colors.tileBg)
                    .cornerRadius(20.dp)
                    .clickable(actionRunCallback<RefreshAction>())
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = colors.accent,
                        fontSize = 15.sp,
                    ),
                )
            }
        }
    }

    @Composable
    private fun TaskTile(task: CUTask, colors: WidgetColors) {
        val uri = Uri.Builder()
            .scheme("https")
            .authority("app.clickup.com")
            .appendPath("t")
            .appendPath(task.id)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)

        // Outer Row: accent background = left stripe.
        // Inner Row: tile background covers everything except the 4dp stripe.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 3.dp)
                .background(colors.accent)
                .cornerRadius(10.dp)
                .clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(colors.tileBg)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.name,
                    style = TextStyle(
                        color = colors.text,
                        fontSize = 13.sp,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "›",
                    style = TextStyle(
                        color = colors.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }

    @Composable
    private fun SyncingState(colors: WidgetColors) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "↻  Updating…",
                    style = TextStyle(
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Loading tasks",
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }

    @Composable
    private fun EmptyState(colors: WidgetColors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No tasks",
                    style = TextStyle(
                        color = colors.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Tap to refresh",
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }

    @Composable
    private fun FullErrorState(error: String, colors: WidgetColors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Could not load tasks",
                    style = TextStyle(
                        color = colors.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = error,
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(12.dp))
                Box(
                    modifier = GlanceModifier
                        .background(colors.tileBg)
                        .cornerRadius(20.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↻  Retry",
                        style = TextStyle(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun ErrorBanner(error: String, colors: WidgetColors) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(colors.tileBg)
                .cornerRadius(8.dp)
                .clickable(actionRunCallback<RefreshAction>())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚠",
                style = TextStyle(color = colors.error, fontSize = 11.sp),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "Sync failed — tap to retry",
                style = TextStyle(color = colors.error, fontSize = 11.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    companion object {
        private const val MAX_VISIBLE_TASKS = 15
        // 2× the 15-min periodic sync interval; syncing flag older than this is considered stale.
        private const val SYNC_STALE_THRESHOLD_MS = 30 * 60 * 1000L
        // Shared key used by TaskSyncWorker and WidgetConfigActivity to bump Glance's DataStore
        // state and trigger a re-render. Both must reference this constant — never inline "refresh".
        internal val REFRESH_KEY = intPreferencesKey("refresh")
    }
}
