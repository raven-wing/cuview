package io.github.raven_wing.cuview.data.storage

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Integration tests for SecurePreferences per-widget target storage.
// SecurePreferencesTargetEncodingTest covers the pure encode/decode helpers; these
// tests verify that the full round-trip through SharedPreferences is correct and that
// different widget IDs remain isolated from each other.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurePreferencesWidgetTargetTest {

    private fun makePrefs() = SecurePreferences(
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_widget_target", Context.MODE_PRIVATE),
    )

    @Test
    fun viewId_returnsNullWhenNoTargetSet() {
        assertNull(makePrefs().viewId(1))
    }

    @Test
    fun isListTarget_returnsFalseWhenNoTargetSet() {
        assertFalse(makePrefs().isListTarget(1))
    }

    @Test
    fun setTarget_listTarget_viewIdReturnsCorrectId() {
        val prefs = makePrefs()
        prefs.setTarget(1, "list-xyz", isListTarget = true)
        assertEquals("list-xyz", prefs.viewId(1))
    }

    @Test
    fun setTarget_viewTarget_viewIdReturnsCorrectId() {
        val prefs = makePrefs()
        prefs.setTarget(1, "view-abc", isListTarget = false)
        assertEquals("view-abc", prefs.viewId(1))
    }

    @Test
    fun setTarget_listTarget_isListTargetReturnsTrue() {
        val prefs = makePrefs()
        prefs.setTarget(1, "list-xyz", isListTarget = true)
        assertTrue(prefs.isListTarget(1))
    }

    @Test
    fun setTarget_viewTarget_isListTargetReturnsFalse() {
        val prefs = makePrefs()
        prefs.setTarget(1, "view-abc", isListTarget = false)
        assertFalse(prefs.isListTarget(1))
    }

    // Regression: per-widget target keys must include the widgetId so different widgets
    // don't overwrite each other's configuration.
    @Test
    fun setTarget_differentWidgets_maintainIsolation() {
        val prefs = makePrefs()
        prefs.setTarget(1, "list-aaa", isListTarget = true)
        prefs.setTarget(2, "view-bbb", isListTarget = false)

        assertEquals("list-aaa", prefs.viewId(1))
        assertTrue(prefs.isListTarget(1))
        assertEquals("view-bbb", prefs.viewId(2))
        assertFalse(prefs.isListTarget(2))
    }

    @Test
    fun clearWidget_removesTargetForThatWidget() {
        val prefs = makePrefs()
        prefs.setTarget(1, "list-xyz", isListTarget = true)

        prefs.clearWidget(1)

        assertNull(prefs.viewId(1))
        assertFalse(prefs.isListTarget(1))
    }

    @Test
    fun clearWidget_doesNotAffectOtherWidget() {
        val prefs = makePrefs()
        prefs.setTarget(1, "list-aaa", isListTarget = true)
        prefs.setTarget(2, "view-bbb", isListTarget = false)

        prefs.clearWidget(1)

        assertNull(prefs.viewId(1))
        assertEquals("view-bbb", prefs.viewId(2))
    }

    @Test
    fun setTarget_overwritesExistingTarget() {
        val prefs = makePrefs()
        prefs.setTarget(1, "list-old", isListTarget = true)
        prefs.setTarget(1, "view-new", isListTarget = false)

        assertEquals("view-new", prefs.viewId(1))
        assertFalse(prefs.isListTarget(1))
    }
}
