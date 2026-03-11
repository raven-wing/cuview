package io.github.raven_wing.cuview.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the OAuth token and per-widget target configuration.
 *
 * Backed by [EncryptedSharedPreferences] with AES-256-GCM. Tink key derivation is lazy
 * (~100 ms on first access) to avoid blocking the widget render thread.
 *
 * The target is stored as a prefixed string (`"list:<id>"` or `"view:<id>"`) rather than
 * separate keys because [EncryptedSharedPreferences.getBoolean] silently returns the default
 * on a key-type mismatch, making a boolean `isListTarget` field unreliable.
 *
 * Tests inject a plain [SharedPreferences] via the internal constructor to avoid the
 * Android Keystore, which Robolectric does not support.
 */
class SecurePreferences private constructor(lazyPrefs: Lazy<SharedPreferences>) {

    // Production constructor — Tink key derivation is deferred (~100 ms) until first access.
    constructor(context: Context) : this(lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cuview_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    })

    // Test constructor — accepts a plain SharedPreferences so tests don't need the Android
    // Keystore (which Robolectric doesn't support).
    internal constructor(prefs: SharedPreferences) : this(lazyOf(prefs))

    private val prefs: SharedPreferences by lazyPrefs

    // Shared across all widgets
    var apiToken: String?
        get() = prefs.getString(KEY_API_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value).apply()

    // Per-widget target: stored as "list:<id>" or "view:<id>" to avoid boolean storage.
    // EncryptedSharedPreferences getBoolean() silently returns the default when the key
    // type mismatches — encoding the type in the string is more reliable.

    fun viewId(widgetId: Int): String? = prefs.getString(targetKey(widgetId), null)?.let(::decodeId)

    fun isListTarget(widgetId: Int): Boolean = prefs.getString(targetKey(widgetId), null)
        ?.let(::decodeIsListTarget) ?: false

    fun setTarget(widgetId: Int, id: String, isListTarget: Boolean) {
        prefs.edit().putString(targetKey(widgetId), encodeTarget(id, isListTarget)).apply()
    }

    fun clearWidget(widgetId: Int) {
        prefs.edit().remove(targetKey(widgetId)).apply()
    }

    private fun targetKey(widgetId: Int) = "target_$widgetId"

    companion object {
        private const val KEY_API_TOKEN = "api_token"
        internal const val LIST_PREFIX = "list:"
        internal const val VIEW_PREFIX = "view:"

        internal fun encodeTarget(id: String, isListTarget: Boolean): String =
            if (isListTarget) "$LIST_PREFIX$id" else "$VIEW_PREFIX$id"

        internal fun decodeId(encoded: String): String = when {
            encoded.startsWith(LIST_PREFIX) -> encoded.removePrefix(LIST_PREFIX)
            encoded.startsWith(VIEW_PREFIX) -> encoded.removePrefix(VIEW_PREFIX)
            else -> encoded
        }

        internal fun decodeIsListTarget(encoded: String): Boolean = encoded.startsWith(LIST_PREFIX)
    }
}
