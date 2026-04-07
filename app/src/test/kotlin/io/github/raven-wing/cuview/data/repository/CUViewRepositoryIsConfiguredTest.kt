package io.github.raven_wing.cuview.data.repository

import android.content.Context
import io.github.raven_wing.cuview.data.model.TasksSource
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Regression test: CUViewRepository used to create a new SecurePreferences instance inside
// isConfigured(), separate from the instance used for syncTasks(). TaskSyncWorker called
// isConfigured() and then constructed a new CUViewRepository, causing two independent Tink
// key-derivation initializations (~100 ms each) on every sync for a configured widget.
//
// The fix adds SecurePreferences as a constructor parameter with a default so the same
// instance is reused across all calls on a single repository.
//
// These tests also verify that the injected SecurePreferences is actually consulted, not a
// separate internally-constructed one.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CUViewRepositoryIsConfiguredTest {

    private lateinit var securePrefs: SecurePreferences
    private lateinit var repository: CUViewRepository

    private val app get() = RuntimeEnvironment.getApplication()
    private val taskPrefs by lazy {
        app.getSharedPreferences("test_repo_is_configured_tasks", Context.MODE_PRIVATE)
    }

    private fun taskStorage(widgetId: Int) = TaskStorage(taskPrefs, widgetId)

    @Before
    fun setUp() {
        securePrefs = SecurePreferences(
            app.getSharedPreferences("test_repo_is_configured", Context.MODE_PRIVATE)
        )
        repository = CUViewRepository(
            context = app,
            securePreferences = securePrefs,
            taskStorageFor = ::taskStorage,
        )
    }

    @Test
    fun isConfigured_returnsFalse_whenNeitherTokenNorTasksSourceSet() {
        assertFalse(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_returnsFalse_whenTokenSetButNoTasksSource() {
        securePrefs.apiToken = "pk_test_token"

        assertFalse(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_returnsFalse_whenTasksSourceSetButNoToken() {
        taskStorage(1).saveTasksSource(TasksSource.View("view-123", "My View"))

        assertFalse(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_returnsTrue_whenBothTokenAndTasksSourceSet() {
        securePrefs.apiToken = "pk_test_token"
        taskStorage(1).saveTasksSource(TasksSource.View("view-123", "My View"))

        assertTrue(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_isPerWidget_differentWidgetsAreIndependent() {
        securePrefs.apiToken = "pk_test_token"
        taskStorage(1).saveTasksSource(TasksSource.View("view-123", "My View"))

        assertTrue(repository.isConfigured(widgetId = 1))
        assertFalse(repository.isConfigured(widgetId = 2))
    }

    @Test
    fun isConfigured_returnsFalse_afterWidgetTasksSourceCleared() {
        securePrefs.apiToken = "pk_test_token"
        taskStorage(1).saveTasksSource(TasksSource.View("view-123", "My View"))
        taskStorage(1).clear()

        assertFalse(repository.isConfigured(widgetId = 1))
    }
}
