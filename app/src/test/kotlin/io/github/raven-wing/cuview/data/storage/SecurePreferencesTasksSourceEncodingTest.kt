package io.github.raven_wing.cuview.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression test: EncryptedSharedPreferences.getBoolean() silently returns the default
// value (false) when the stored type doesn't match, causing list tasks sources to be treated
// as view tasks sources and hitting the wrong ClickUp endpoint. The fix encodes the tasks
// source type as a string prefix ("list:" / "view:") so only getString/putString are used —
// no boolean storage.
class SecurePreferencesTasksSourceEncodingTest {

    @Test
    fun encodeTasksSource_listTasksSource_startsWithListPrefix() {
        val encoded = SecurePreferences.encodeTasksSource("abc123", isListTasksSource = true)
        assertTrue(encoded.startsWith(SecurePreferences.LIST_PREFIX))
    }

    @Test
    fun encodeTasksSource_viewTasksSource_startsWithViewPrefix() {
        val encoded = SecurePreferences.encodeTasksSource("abc123", isListTasksSource = false)
        assertTrue(encoded.startsWith(SecurePreferences.VIEW_PREFIX))
    }

    @Test
    fun decodeIsListTasksSource_listEncoded_returnsTrue() {
        val encoded = SecurePreferences.encodeTasksSource("abc123", isListTasksSource = true)
        assertTrue(SecurePreferences.decodeIsListTasksSource(encoded))
    }

    @Test
    fun decodeIsListTasksSource_viewEncoded_returnsFalse() {
        val encoded = SecurePreferences.encodeTasksSource("abc123", isListTasksSource = false)
        assertFalse(SecurePreferences.decodeIsListTasksSource(encoded))
    }

    @Test
    fun decodeId_listEncoded_returnsBareId() {
        val encoded = SecurePreferences.encodeTasksSource("abc123", isListTasksSource = true)
        assertEquals("abc123", SecurePreferences.decodeId(encoded))
    }

    @Test
    fun decodeId_viewEncoded_returnsBareId() {
        val encoded = SecurePreferences.encodeTasksSource("xyz789", isListTasksSource = false)
        assertEquals("xyz789", SecurePreferences.decodeId(encoded))
    }

    @Test
    fun roundTrip_listTasksSource_preservesIdAndType() {
        val id = "9a2f7c"
        val encoded = SecurePreferences.encodeTasksSource(id, isListTasksSource = true)
        assertEquals(id, SecurePreferences.decodeId(encoded))
        assertTrue(SecurePreferences.decodeIsListTasksSource(encoded))
    }

    @Test
    fun roundTrip_viewTasksSource_preservesIdAndType() {
        val id = "9a2f7c"
        val encoded = SecurePreferences.encodeTasksSource(id, isListTasksSource = false)
        assertEquals(id, SecurePreferences.decodeId(encoded))
        assertFalse(SecurePreferences.decodeIsListTasksSource(encoded))
    }
}
