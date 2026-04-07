package io.github.raven_wing.cuview.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import io.github.raven_wing.cuview.data.storage.TaskStorage
import io.github.raven_wing.cuview.worker.TaskSyncWorker

/**
 * Handles taps on the ↻ refresh button in the widget.
 *
 * Immediately sets the syncing flag and re-renders the widget (so the UI reflects the
 * in-progress state), then enqueues a one-off [TaskSyncWorker] with no network constraint.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        TaskStorage(context, widgetId).setSyncing(true)
        CUViewWidget().updateAll(context)
        TaskSyncWorker.enqueueImmediateSync(context, widgetId)
    }
}
