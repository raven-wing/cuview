package io.github.raven_wing.cuview.data.repository

import android.content.Context
import io.github.raven_wing.cuview.data.storage.SecurePreferences
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

    @Before
    fun setUp() {
        val sharedPrefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_repo_is_configured", Context.MODE_PRIVATE)
        securePrefs = SecurePreferences(sharedPrefs)
        repository = CUViewRepository(
            context = RuntimeEnvironment.getApplication(),
            securePreferences = securePrefs,
        )
    }

    @Test
    fun isConfigured_returnsFalse_whenNeitherTokenNorTargetSet() {
        assertFalse(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_returnsFalse_whenTokenSetButNoTarget() {
        securePrefs.apiToken = "pk_test_token"

        assertFalse(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_returnsFalse_whenTargetSetButNoToken() {
        securePrefs.setTarget(widgetId = 1, id = "view-123", isListTarget = false)

        assertFalse(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_returnsTrue_whenBothTokenAndTargetSet() {
        securePrefs.apiToken = "pk_test_token"
        securePrefs.setTarget(widgetId = 1, id = "view-123", isListTarget = false)

        assertTrue(repository.isConfigured(widgetId = 1))
    }

    @Test
    fun isConfigured_isPerWidget_differentWidgetsAreIndependent() {
        securePrefs.apiToken = "pk_test_token"
        securePrefs.setTarget(widgetId = 1, id = "view-123", isListTarget = false)

        assertTrue(repository.isConfigured(widgetId = 1))
        assertFalse(repository.isConfigured(widgetId = 2))
    }

    @Test
    fun isConfigured_returnsFalse_afterWidgetTargetCleared() {
        securePrefs.apiToken = "pk_test_token"
        securePrefs.setTarget(widgetId = 1, id = "view-123", isListTarget = false)
        securePrefs.clearWidget(widgetId = 1)

        assertFalse(repository.isConfigured(widgetId = 1))
    }
}
