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

// Integration tests for SecurePreferences per-widget tasks source storage.
// SecurePreferencesTasksSourceEncodingTest covers the pure encode/decode helpers; these
// tests verify that the full round-trip through SharedPreferences is correct and that
// different widget IDs remain isolated from each other.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurePreferencesWidgetTasksSourceTest {

    private fun makePrefs() = SecurePreferences(
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_widget_tasks_source", Context.MODE_PRIVATE),
    )

    @Test
    fun viewId_returnsNullWhenNoTasksSourceSet() {
        assertNull(makePrefs().viewId(1))
    }

    @Test
    fun isListTasksSource_returnsFalseWhenNoTasksSourceSet() {
        assertFalse(makePrefs().isListTasksSource(1))
    }

    @Test
    fun setTasksSource_listTasksSource_viewIdReturnsCorrectId() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "list-xyz", isListTasksSource = true)
        assertEquals("list-xyz", prefs.viewId(1))
    }

    @Test
    fun setTasksSource_viewTasksSource_viewIdReturnsCorrectId() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "view-abc", isListTasksSource = false)
        assertEquals("view-abc", prefs.viewId(1))
    }

    @Test
    fun setTasksSource_listTasksSource_isListTasksSourceReturnsTrue() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "list-xyz", isListTasksSource = true)
        assertTrue(prefs.isListTasksSource(1))
    }

    @Test
    fun setTasksSource_viewTasksSource_isListTasksSourceReturnsFalse() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "view-abc", isListTasksSource = false)
        assertFalse(prefs.isListTasksSource(1))
    }

    // Regression: per-widget tasks source keys must include the widgetId so different widgets
    // don't overwrite each other's configuration.
    @Test
    fun setTasksSource_differentWidgets_maintainIsolation() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "list-aaa", isListTasksSource = true)
        prefs.setTasksSource(2, "view-bbb", isListTasksSource = false)

        assertEquals("list-aaa", prefs.viewId(1))
        assertTrue(prefs.isListTasksSource(1))
        assertEquals("view-bbb", prefs.viewId(2))
        assertFalse(prefs.isListTasksSource(2))
    }

    @Test
    fun clearWidget_removesTasksSourceForThatWidget() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "list-xyz", isListTasksSource = true)

        prefs.clearWidget(1)

        assertNull(prefs.viewId(1))
        assertFalse(prefs.isListTasksSource(1))
    }

    @Test
    fun clearWidget_doesNotAffectOtherWidget() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "list-aaa", isListTasksSource = true)
        prefs.setTasksSource(2, "view-bbb", isListTasksSource = false)

        prefs.clearWidget(1)

        assertNull(prefs.viewId(1))
        assertEquals("view-bbb", prefs.viewId(2))
    }

    @Test
    fun setTasksSource_overwritesExistingTasksSource() {
        val prefs = makePrefs()
        prefs.setTasksSource(1, "list-old", isListTasksSource = true)
        prefs.setTasksSource(1, "view-new", isListTasksSource = false)

        assertEquals("view-new", prefs.viewId(1))
        assertFalse(prefs.isListTasksSource(1))
    }
}
