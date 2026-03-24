package io.github.raven_wing.cuview.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.raven_wing.cuview.data.model.CUTask
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Per-widget task cache backed by [EncryptedSharedPreferences].
 *
 * Each widget instance gets its own keyed set of entries (tasks, error, last-updated
 * timestamp, syncing flag, tasks source name) within a shared `"cuview_task_cache"` file.
 *
 * Design contract:
 * - Stale tasks are **preserved** on sync failure — only the error message is updated.
 *   This lets the widget show the previous task list with an error banner instead of
 *   going blank.
 * - The `isSyncing` flag is a best-effort hint. [io.github.raven_wing.cuview.widget.CUViewWidget]
 *   resets it automatically if it appears stale (older than 2× the sync interval).
 *
 * Tests inject a plain [SharedPreferences] via the internal constructor to avoid the
 * Android Keystore, which Robolectric does not support.
 */
class TaskStorage private constructor(lazyPrefs: Lazy<SharedPreferences>, widgetId: Int) {

    // Production constructor — Tink key derivation is deferred until first access,
    // same pattern as SecurePreferences, to avoid blocking the widget render thread.
    constructor(context: Context, widgetId: Int) : this(lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cuview_task_cache",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }, widgetId)

    // Test constructor — accepts a plain SharedPreferences so tests don't need the Android
    // Keystore (which Robolectric doesn't support).
    internal constructor(prefs: SharedPreferences, widgetId: Int) : this(lazyOf(prefs), widgetId)

    private val prefs: SharedPreferences by lazyPrefs

    private val keyTasksJson = "tasks_json_$widgetId"
    private val keyLastError = "last_error_$widgetId"
    private val keyLastUpdatedMs = "last_updated_ms_$widgetId"
    private val keyIsSyncing = "is_syncing_$widgetId"
    private val keySyncStartMs = "sync_start_ms_$widgetId"
    private val keyTasksSourceName = "tasks_source_name_$widgetId"
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

    fun saveTasksSourceName(name: String) {
        prefs.edit().putString(keyTasksSourceName, name).apply()
    }

    fun loadTasksSourceName(): String? = prefs.getString(keyTasksSourceName, null)

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
            .remove(keyTasksSourceName)
            .remove(keyThemeId)
            .apply()
    }
}
