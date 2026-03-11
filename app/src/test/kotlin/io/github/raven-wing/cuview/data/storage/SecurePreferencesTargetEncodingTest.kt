package io.github.raven_wing.cuview.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression test: EncryptedSharedPreferences.getBoolean() silently returns the default
// value (false) when the stored type doesn't match, causing list targets to be treated as
// view targets and hitting the wrong ClickUp endpoint. The fix encodes the target type as a
// string prefix ("list:" / "view:") so only getString/putString are used — no boolean storage.
class SecurePreferencesTargetEncodingTest {

    @Test
    fun encodeTarget_listTarget_startsWithListPrefix() {
        val encoded = SecurePreferences.encodeTarget("abc123", isListTarget = true)
        assertTrue(encoded.startsWith(SecurePreferences.LIST_PREFIX))
    }

    @Test
    fun encodeTarget_viewTarget_startsWithViewPrefix() {
        val encoded = SecurePreferences.encodeTarget("abc123", isListTarget = false)
        assertTrue(encoded.startsWith(SecurePreferences.VIEW_PREFIX))
    }

    @Test
    fun decodeIsListTarget_listEncoded_returnsTrue() {
        val encoded = SecurePreferences.encodeTarget("abc123", isListTarget = true)
        assertTrue(SecurePreferences.decodeIsListTarget(encoded))
    }

    @Test
    fun decodeIsListTarget_viewEncoded_returnsFalse() {
        val encoded = SecurePreferences.encodeTarget("abc123", isListTarget = false)
        assertFalse(SecurePreferences.decodeIsListTarget(encoded))
    }

    @Test
    fun decodeId_listEncoded_returnsBareId() {
        val encoded = SecurePreferences.encodeTarget("abc123", isListTarget = true)
        assertEquals("abc123", SecurePreferences.decodeId(encoded))
    }

    @Test
    fun decodeId_viewEncoded_returnsBareId() {
        val encoded = SecurePreferences.encodeTarget("xyz789", isListTarget = false)
        assertEquals("xyz789", SecurePreferences.decodeId(encoded))
    }

    @Test
    fun roundTrip_listTarget_preservesIdAndType() {
        val id = "9a2f7c"
        val encoded = SecurePreferences.encodeTarget(id, isListTarget = true)
        assertEquals(id, SecurePreferences.decodeId(encoded))
        assertTrue(SecurePreferences.decodeIsListTarget(encoded))
    }

    @Test
    fun roundTrip_viewTarget_preservesIdAndType() {
        val id = "9a2f7c"
        val encoded = SecurePreferences.encodeTarget(id, isListTarget = false)
        assertEquals(id, SecurePreferences.decodeId(encoded))
        assertFalse(SecurePreferences.decodeIsListTarget(encoded))
    }
}
