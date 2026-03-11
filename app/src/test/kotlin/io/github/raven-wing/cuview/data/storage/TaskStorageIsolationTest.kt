package io.github.raven_wing.cuview.data.storage

import android.content.Context
import io.github.raven_wing.cuview.data.model.Task
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

        storage1.saveTasks(listOf(Task("t1", "Widget 1 task")))
        storage2.saveTasks(listOf(Task("t2", "Widget 2 task A"), Task("t3", "Widget 2 task B")))

        assertEquals(listOf(Task("t1", "Widget 1 task")), storage1.loadTasks())
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

        storage1.saveTasks(listOf(Task("t1", "Widget 1 task")))
        storage2.saveTasks(listOf(Task("t2", "Widget 2 task")))

        storage1.clear()

        assertTrue(storage1.loadTasks().isEmpty())
        assertEquals(listOf(Task("t2", "Widget 2 task")), storage2.loadTasks())
    }

    // Bug A regression: a fresh TaskStorage must not report isSyncing=true. Before the fix,
    // the widget showed "No tasks" because isSyncing defaulted to true in some code paths,
    // suppressing the empty-state UI immediately after the widget was added.
    @Test
    fun freshStorage_isSyncingDefaultsFalse() {
        val storage = storage(99)

        assertFalse(storage.isSyncing())
    }

    // Bug B regression: saveTargetName / loadTargetName must be keyed by widgetId.
    // Before the fix, all widgets shared the same SharedPreferences key so widget 2 would
    // display widget 1's list name.
    @Test
    fun targetName_isIsolatedPerWidget() {
        val storage1 = storage(1)
        val storage2 = storage(2)

        storage1.saveTargetName("Sprint Board")
        storage2.saveTargetName("Backlog")

        assertEquals("Sprint Board", storage1.loadTargetName())
        assertEquals("Backlog", storage2.loadTargetName())
    }

    // Bug C regression: clear() must wipe every field, including isSyncing and targetName.
    // When those two fields were introduced they were initially omitted from clear(), which
    // caused stale syncing/name state to leak into the widget after it was reconfigured.
    @Test
    fun clear_removesAllFields_includingSyncingAndTargetName() {
        val storage = storage(1)

        storage.saveTasks(listOf(Task("t1", "Task One")))
        storage.saveError("Something went wrong")
        storage.setSyncing(true)
        storage.saveTargetName("My List")

        storage.clear()

        assertTrue(storage.loadTasks().isEmpty())
        assertNull(storage.loadError())
        assertFalse(storage.isSyncing())
        assertNull(storage.loadTargetName())
    }
}
