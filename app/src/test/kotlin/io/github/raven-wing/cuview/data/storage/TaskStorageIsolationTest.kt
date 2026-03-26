package io.github.raven_wing.cuview.data.storage

import android.content.Context
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.TasksSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Regression test: all widgets used to share a single TaskStorage, so widget 2 always showed
// widget 1's tasks. The fix keys all storage by appWidgetId.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskStorageIsolationTest {

    // Use the internal test constructor to avoid EncryptedSharedPreferences / Keystore
    // which Robolectric doesn't support.
    private val sharedPrefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("test_task_isolation", Context.MODE_PRIVATE)

    private fun storage(widgetId: Int) = TaskStorage(sharedPrefs, widgetId)

    @Test
    fun differentWidgetIds_maintainSeparateTaskCaches() {
        val storage1 = storage(1)
        val storage2 = storage(2)

        storage1.saveTasks(listOf(CUTask("t1", "Widget 1 task")))
        storage2.saveTasks(listOf(CUTask("t2", "Widget 2 task A"), CUTask("t3", "Widget 2 task B")))

        assertEquals(listOf(CUTask("t1", "Widget 1 task")), storage1.loadTasks())
        assertEquals(2, storage2.loadTasks().size)
        assertEquals("t2", storage2.loadTasks()[0].id)
        assertEquals("t3", storage2.loadTasks()[1].id)
    }

    @Test
    fun errorState_isIsolatedPerWidget() {
        val storage1 = storage(1)
        val storage2 = storage(2)

        storage1.saveError("Sync failed for widget 1")

        assertEquals("Sync failed for widget 1", storage1.loadError())
        assertNull(storage2.loadError())
    }

    @Test
    fun syncingState_isIsolatedPerWidget() {
        val storage1 = storage(1)
        val storage2 = storage(2)

        storage1.setSyncing(true)

        assertTrue(storage1.isSyncing())
        assertFalse(storage2.isSyncing())
    }

    @Test
    fun clearingOneWidget_doesNotAffectAnother() {
        val storage1 = storage(1)
        val storage2 = storage(2)

        storage1.saveTasks(listOf(CUTask("t1", "Widget 1 task")))
        storage2.saveTasks(listOf(CUTask("t2", "Widget 2 task")))

        storage1.clear()

        assertTrue(storage1.loadTasks().isEmpty())
        assertEquals(listOf(CUTask("t2", "Widget 2 task")), storage2.loadTasks())
    }

    // Bug A regression: a fresh TaskStorage must not report isSyncing=true. Before the fix,
    // the widget showed "No tasks" because isSyncing defaulted to true in some code paths,
    // suppressing the empty-state UI immediately after the widget was added.
    @Test
    fun freshStorage_isSyncingDefaultsFalse() {
        val storage = storage(99)

        assertFalse(storage.isSyncing())
    }

    // Bug B regression: tasks source storage must be keyed by widgetId.
    // Before the fix, all widgets shared the same SharedPreferences key so widget 2 would
    // display widget 1's list name.
    @Test
    fun tasksSource_isIsolatedPerWidget() {
        val storage1 = storage(1)
        val storage2 = storage(2)

        storage1.saveTasksSource(TasksSource.List("list-aaa", "Sprint Board"))
        storage2.saveTasksSource(TasksSource.View("view-bbb", "Backlog"))

        assertTrue(storage1.loadTasksSource() is TasksSource.List)
        assertEquals("Sprint Board", storage1.loadTasksSource()?.label)
        assertTrue(storage2.loadTasksSource() is TasksSource.View)
        assertEquals("Backlog", storage2.loadTasksSource()?.label)
    }

    // Bug C regression: clear() must wipe every field, including isSyncing and tasks source.
    // When those two fields were introduced they were initially omitted from clear(), which
    // caused stale syncing/source state to leak into the widget after it was reconfigured.
    @Test
    fun clear_removesAllFields_includingSyncingAndTasksSource() {
        val storage = storage(1)

        storage.saveTasks(listOf(CUTask("t1", "Task One")))
        storage.saveError("Something went wrong")
        storage.setSyncing(true)
        storage.saveTasksSource(TasksSource.View("view-123", "My List"))

        storage.clear()

        assertTrue(storage.loadTasks().isEmpty())
        assertNull(storage.loadError())
        assertFalse(storage.isSyncing())
        assertNull(storage.loadTasksSource())
    }
}
