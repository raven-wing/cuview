package io.github.raven_wing.cuview.data.storage

import android.content.Context
import org.junit.Assert.assertEquals
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
    fun tasksSource_returnsNullWhenNoTasksSourceSet() {
        assertNull(makePrefs().tasksSource(1))
    }

    @Test
    fun setListTasksSource_returnsListSourceWithCorrectId() {
        val prefs = makePrefs()
        prefs.setListTasksSource(1, "list-xyz")
        val source = prefs.tasksSource(1)
        assertTrue(source is StoredTasksSource.List)
        assertEquals("list-xyz", source?.id)
    }

    @Test
    fun setViewTasksSource_returnsViewSourceWithCorrectId() {
        val prefs = makePrefs()
        prefs.setViewTasksSource(1, "view-abc")
        val source = prefs.tasksSource(1)
        assertTrue(source is StoredTasksSource.View)
        assertEquals("view-abc", source?.id)
    }

    // Regression: per-widget tasks source keys must include the widgetId so different widgets
    // don't overwrite each other's configuration.
    @Test
    fun setTasksSource_differentWidgets_maintainIsolation() {
        val prefs = makePrefs()
        prefs.setListTasksSource(1, "list-aaa")
        prefs.setViewTasksSource(2, "view-bbb")

        assertTrue(prefs.tasksSource(1) is StoredTasksSource.List)
        assertEquals("list-aaa", prefs.tasksSource(1)?.id)
        assertTrue(prefs.tasksSource(2) is StoredTasksSource.View)
        assertEquals("view-bbb", prefs.tasksSource(2)?.id)
    }

    @Test
    fun clearWidget_removesTasksSourceForThatWidget() {
        val prefs = makePrefs()
        prefs.setListTasksSource(1, "list-xyz")

        prefs.clearWidget(1)

        assertNull(prefs.tasksSource(1))
    }

    @Test
    fun clearWidget_doesNotAffectOtherWidget() {
        val prefs = makePrefs()
        prefs.setListTasksSource(1, "list-aaa")
        prefs.setViewTasksSource(2, "view-bbb")

        prefs.clearWidget(1)

        assertNull(prefs.tasksSource(1))
        assertEquals("view-bbb", prefs.tasksSource(2)?.id)
    }

    @Test
    fun setTasksSource_overwritesExistingTasksSource() {
        val prefs = makePrefs()
        prefs.setListTasksSource(1, "list-old")
        prefs.setViewTasksSource(1, "view-new")

        assertTrue(prefs.tasksSource(1) is StoredTasksSource.View)
        assertEquals("view-new", prefs.tasksSource(1)?.id)
    }
}
