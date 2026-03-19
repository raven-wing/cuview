package io.github.raven_wing.cuview.data.storage

import android.content.Context
import io.github.raven_wing.cuview.data.model.CUTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskStorageTimestampTest {

    // Use the internal test constructor to avoid EncryptedSharedPreferences / Keystore
    // which Robolectric doesn't support.
    private val sharedPrefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("test_task_timestamp", Context.MODE_PRIVATE)

    private fun storage(widgetId: Int) = TaskStorage(sharedPrefs, widgetId)

    @Test
    fun loadLastUpdatedMs_returnsZeroBeforeSave() {
        assertEquals(0L, storage(1).loadLastUpdatedMs())
    }

    @Test
    fun saveTasks_updatesLastUpdatedMs() {
        val s = storage(2)
        val before = System.currentTimeMillis()

        s.saveTasks(listOf(CUTask("t1", "Task")))

        val ts = s.loadLastUpdatedMs()
        assertTrue("timestamp should be >= before save", ts >= before)
        assertTrue("timestamp should be <= now", ts <= System.currentTimeMillis())
    }

    @Test
    fun clear_resetsLastUpdatedMsToZero() {
        val s = storage(3)
        s.saveTasks(listOf(CUTask("t1", "Task")))

        s.clear()

        assertEquals(0L, s.loadLastUpdatedMs())
    }

    // Regression: timestamp must be isolated per widget, just like every other field.
    @Test
    fun lastUpdatedMs_isIsolatedPerWidget() {
        val s1 = storage(4)
        val s2 = storage(5)

        s1.saveTasks(listOf(CUTask("t1", "Task")))

        assertTrue(s1.loadLastUpdatedMs() > 0)
        assertEquals(0L, s2.loadLastUpdatedMs())
    }
}
