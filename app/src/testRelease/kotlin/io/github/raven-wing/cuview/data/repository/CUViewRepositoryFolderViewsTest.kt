package io.github.raven_wing.cuview.data.repository

import android.content.Context
import io.github.raven_wing.cuview.data.network.CUViewApiService
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Regression test: fetchFolderViews used to swallow API errors by calling getOrNull() on the
// service result, returning Result.success(emptyList()) even when the network call failed.
// FolderContentsLevel then collapsed Failure and null states into an empty views list with
// no error message, inconsistent with every other loading state in the config browse UI.
// The fix propagates failures from the service using getOrThrow() inside a try/catch.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CUViewRepositoryFolderViewsTest {

    private val mockServer = MockWebServer()
    private lateinit var repository: CUViewRepository

    @Before
    fun setUp() {
        mockServer.start()
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_repo_folder_views", Context.MODE_PRIVATE)
        repository = CUViewRepository(
            context = RuntimeEnvironment.getApplication(),
            securePreferences = SecurePreferences(prefs),
            apiService = CUViewApiService(baseUrl = "http://${mockServer.hostName}:${mockServer.port}"),
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun fetchFolderViews_returnsFailure_whenApiReturnsHttpError() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(403))

        val result = repository.fetchFolderViews("folder-1", "bad-token")

        assertTrue("Expected failure but got: $result", result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
    }

    @Test
    fun fetchFolderViews_returnsSuccess_whenApiReturnsValidViews() = runBlocking {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"views": [{"id": "v1", "name": "List View", "type": "list"}]}""",
            ),
        )

        val result = repository.fetchFolderViews("folder-1", "token")

        assertTrue(result.isSuccess)
        val views = result.getOrThrow()
        assertFalse("Expected non-empty views list", views.isEmpty())
        assertTrue(views.any { it.id == "v1" })
    }

    @Test
    fun fetchFolderViews_returnsEmptyList_whenApiReturnsNoListTypeViews() = runBlocking {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"views": [{"id": "v1", "name": "Board", "type": "board"}]}""",
            ),
        )

        val result = repository.fetchFolderViews("folder-1", "token")

        assertTrue(result.isSuccess)
        assertTrue("Expected empty list when no list-type views", result.getOrThrow().isEmpty())
    }
}
