package io.github.raven_wing.cuview.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.raven_wing.cuview.data.model.TasksSource

/**
 * Encrypted storage for the OAuth token and per-widget tasks source configuration.
 *
 * Backed by [EncryptedSharedPreferences] with AES-256-GCM. Construction is slow (~100 ms)
 * so callers should create this on a background thread.
 *
 * The tasks source is stored as a prefixed string (`"list:<id>"` or `"view:<id>"`) rather than
 * separate keys because [EncryptedSharedPreferences.getBoolean] silently returns the default
 * on a key-type mismatch. [tasksSource] decodes both the id and the type in one read.
 *
 * Tests inject a plain [SharedPreferences] via the internal constructor to avoid the
 * Android Keystore, which Robolectric does not support.
 */
class SecurePreferences(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        EncryptedSharedPreferences.create(
            context,
            "cuview_secure_prefs",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    )

    // Shared across all widgets
    var apiToken: String?
        get() = prefs.getString(KEY_API_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value).apply()

    // Per-widget tasks source: stored as "list:<id>" or "view:<id>" to avoid boolean storage.
    // EncryptedSharedPreferences getBoolean() silently returns the default when the key
    // type mismatches — encoding the type in the string is more reliable.

    fun tasksSource(widgetId: Int): TasksSource? {
        val encoded = prefs.getString(tasksSourceKey(widgetId), null) ?: return null
        val id = decodeId(encoded)
        return if (decodeIsListTasksSource(encoded)) TasksSource.List(id) else TasksSource.View(id)
    }

    fun setViewTasksSource(widgetId: Int, id: String) {
        prefs.edit().putString(tasksSourceKey(widgetId), encodeViewTasksSource(id)).apply()
    }

    fun setListTasksSource(widgetId: Int, id: String) {
        prefs.edit().putString(tasksSourceKey(widgetId), encodeListTasksSource(id)).apply()
    }

    fun clearWidget(widgetId: Int) {
        prefs.edit().remove(tasksSourceKey(widgetId)).apply()
    }

    private fun tasksSourceKey(widgetId: Int) = "tasks_source_$widgetId"

    companion object {
        private const val KEY_API_TOKEN = "api_token"
        internal const val LIST_PREFIX = "list:"
        internal const val VIEW_PREFIX = "view:"

        internal fun encodeViewTasksSource(id: String): String = "$VIEW_PREFIX$id"
        internal fun encodeListTasksSource(id: String): String = "$LIST_PREFIX$id"

        internal fun decodeId(encoded: String): String = when {
            encoded.startsWith(LIST_PREFIX) -> encoded.removePrefix(LIST_PREFIX)
            encoded.startsWith(VIEW_PREFIX) -> encoded.removePrefix(VIEW_PREFIX)
            else -> encoded
        }

        internal fun decodeIsListTasksSource(encoded: String): Boolean = encoded.startsWith(LIST_PREFIX)
    }
}
