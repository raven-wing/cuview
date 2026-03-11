package io.github.raven_wing.cuview.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.raven_wing.cuview.data.repository.CUViewRepository
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import io.github.raven_wing.cuview.worker.TaskSyncWorker

/**
 * Entry point for Android's AppWidget system.
 *
 * - [onUpdate] — called on device reboot or system-initiated refresh; enqueues an immediate
 *   sync for each widget instance.
 * - [onDeleted] — cancels the periodic WorkManager sync and clears all stored data for the
 *   removed widget instance.
 *
 * Periodic sync scheduling is intentionally **not** done here — it is set up in
 * [io.github.raven_wing.cuview.ui.config.WidgetConfigActivity.onConfigSaved] so the
 * interval only starts after the user has configured the widget.
 */
class CUViewWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = CUViewWidget()

    // Periodic syncs are scheduled per-widget in WidgetConfigActivity.onConfigSaved.
    // onEnabled / onDisabled are intentionally omitted.

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Called on reboot / periodic system refresh — sync each widget individually.
        // Skip widgets that haven't been configured yet (e.g. onUpdate fires during the
        // WidgetConfigActivity flow before the user saves their selection).
        appWidgetIds.forEach { widgetId ->
            if (CUViewRepository.isConfigured(context, widgetId)) {
                TaskSyncWorker.enqueueImmediateSync(context, widgetId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = SecurePreferences(context)
        appWidgetIds.forEach { widgetId ->
            TaskSyncWorker.cancelPeriodicSync(context, widgetId)
            TaskStorage(context, widgetId).clear()
            prefs.clearWidget(widgetId)
        }
    }
}
