package io.github.raven_wing.cuview.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.TasksSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Per-widget task cache backed by [SharedPreferences] (encrypted via [EncryptedSharedPreferences]
 * in production).
 *
 * Each widget instance gets its own keyed set of entries (tasks, error, last-updated
 * timestamp, syncing flag, tasks source name) within a shared `"cuview_task_cache"` file.
 *
 * Design contract:
 * - Cached tasks are **preserved** on sync failure — only the error message is updated.
 *   This lets the widget show the previous task list with an error banner instead of
 *   going blank.
 * - The `isSyncing` flag is a best-effort hint. [io.github.raven_wing.cuview.widget.CUViewWidget]
 *   resets it automatically if it appears stale (older than 2× the sync interval).
 *
 * Tests inject a plain [SharedPreferences] via the internal constructor to avoid the
 * Android Keystore, which Robolectric does not support.
 */
class TaskStorage(private val prefs: SharedPreferences, widgetId: Int) {

    constructor(context: Context, widgetId: Int) : this(
        EncryptedSharedPreferences.create(
            context,
            "cuview_task_cache",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
        widgetId,
    )

    private val keyTasksJson = "tasks_json_$widgetId"
    private val keyLastError = "last_error_$widgetId"
    private val keyLastUpdatedMs = "last_updated_ms_$widgetId"
    private val keyIsSyncing = "is_syncing_$widgetId"
    private val keySyncStartMs = "sync_start_ms_$widgetId"
    private val keyTasksSourceTypeId = "tasks_source_type_id_$widgetId"
    private val keyTasksSourceLabel = "tasks_source_label_$widgetId"
    private val keyThemeId = "theme_id_$widgetId"

    fun saveTasks(tasks: List<CUTask>) {
        prefs.edit()
            .putString(keyTasksJson, Json.encodeToString(tasks))
            .putLong(keyLastUpdatedMs, System.currentTimeMillis())
            .apply()
    }

    fun loadTasks(): List<CUTask> {
        val json = prefs.getString(keyTasksJson, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveError(message: String) {
        prefs.edit().putString(keyLastError, message).apply()
    }

    fun clearError() {
        prefs.edit().remove(keyLastError).apply()
    }

    fun loadError(): String? = prefs.getString(keyLastError, null)

    fun loadLastUpdatedMs(): Long = prefs.getLong(keyLastUpdatedMs, 0L)

    fun setSyncing(syncing: Boolean) {
        prefs.edit().putBoolean(keyIsSyncing, syncing).apply()
    }

    fun isSyncing(): Boolean = prefs.getBoolean(keyIsSyncing, false)

    fun saveSyncStartMs() {
        prefs.edit().putLong(keySyncStartMs, System.currentTimeMillis()).apply()
    }

    fun loadSyncStartMs(): Long = prefs.getLong(keySyncStartMs, 0L)

    fun saveViewTasksSource(id: String, label: String) {
        prefs.edit().putString(keyTasksSourceTypeId, "view:$id").putString(keyTasksSourceLabel, label).apply()
    }

    fun saveListTasksSource(id: String, label: String) {
        prefs.edit().putString(keyTasksSourceTypeId, "list:$id").putString(keyTasksSourceLabel, label).apply()
    }

    fun loadTasksSource(): TasksSource? {
        val typeId = prefs.getString(keyTasksSourceTypeId, null) ?: return null
        val label = prefs.getString(keyTasksSourceLabel, null) ?: return null
        return if (typeId.startsWith("list:"))
            TasksSource.List(typeId.removePrefix("list:"), label)
        else
            TasksSource.View(typeId.removePrefix("view:"), label)
    }

    fun saveThemeId(themeId: String) {
        prefs.edit().putString(keyThemeId, themeId).apply()
    }

    fun loadThemeId(): String? = prefs.getString(keyThemeId, null)

    fun clear() {
        prefs.edit()
            .remove(keyTasksJson)
            .remove(keyLastError)
            .remove(keyLastUpdatedMs)
            .remove(keyIsSyncing)
            .remove(keySyncStartMs)
            .remove(keyTasksSourceTypeId)
            .remove(keyTasksSourceLabel)
            .remove(keyThemeId)
            .apply()
    }
}
