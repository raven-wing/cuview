package io.github.raven_wing.cuview.data.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

// Unit tests for the remaining CUViewApiService endpoints (everything except
// fetchTasksByList, which is covered in CUViewApiServiceTest).
class CUViewApiServiceFetchTest {

    private val mockServer = MockWebServer()
    private lateinit var service: CUViewApiService

    @Before
    fun setUp() {
        mockServer.start()
        service = CUViewApiService(token = "test-token", baseUrl = "http://${mockServer.hostName}:${mockServer.port}")
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    // ── fetchTasks ────────────────────────────────────────────────────────────

    @Test
    fun fetchTasks_returnsTasksFromViewEndpoint() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"tasks": [{"id": "t1", "name": "Alpha"}, {"id": "t2", "name": "Beta"}]}""",
            ),
        )

        val tasks = service.fetchTasks("view-1").getOrThrow()

        assertEquals(2, tasks.size)
        assertEquals("t1", tasks[0].id)
        assertEquals("Beta", tasks[1].name)
    }

    @Test
    fun fetchTasks_returnsFailureOnHttpError() {
        mockServer.enqueue(MockResponse().setResponseCode(403))

        val result = service.fetchTasks("view-1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
    }

    // ── fetchWorkspaces ───────────────────────────────────────────────────────

    @Test
    fun fetchWorkspaces_parsesTeamList() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"teams": [{"id": "ws1", "name": "Acme"}, {"id": "ws2", "name": "Umbrella"}]}""",
            ),
        )

        val workspaces = service.fetchWorkspaces().getOrThrow()

        assertEquals(2, workspaces.size)
        assertEquals("ws1", workspaces[0].id)
        assertEquals("Umbrella", workspaces[1].name)
    }

    @Test
    fun fetchWorkspaces_parsesEmptyTeamList() {
        mockServer.enqueue(MockResponse().setBody("""{"teams": []}"""))

        assertTrue(service.fetchWorkspaces().getOrThrow().isEmpty())
    }

    // ── fetchSpaces ───────────────────────────────────────────────────────────

    @Test
    fun fetchSpaces_parsesSpaceList() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"spaces": [{"id": "sp1", "name": "Engineering"}, {"id": "sp2", "name": "Marketing"}]}""",
            ),
        )

        val spaces = service.fetchSpaces("ws1").getOrThrow()

        assertEquals(2, spaces.size)
        assertEquals("sp1", spaces[0].id)
        assertEquals("Marketing", spaces[1].name)
    }

    // ── fetchFolders ──────────────────────────────────────────────────────────

    @Test
    fun fetchFolders_parsesFolderWithNestedLists() {
        mockServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "folders": [
                    {
                      "id": "f1", "name": "Sprint",
                      "lists": [
                        {"id": "l1", "name": "Todo"},
                        {"id": "l2", "name": "Done"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val folders = service.fetchFolders("sp1").getOrThrow()

        assertEquals(1, folders.size)
        val folder = folders[0]
        assertEquals("f1", folder.id)
        assertEquals("Sprint", folder.name)
        assertEquals(2, folder.lists.size)
        assertEquals("l1", folder.lists[0].id)
        assertEquals("Done", folder.lists[1].name)
    }

    @Test
    fun fetchFolders_parsesEmptyFolderList() {
        mockServer.enqueue(MockResponse().setBody("""{"folders": []}"""))

        assertTrue(service.fetchFolders("sp1").getOrThrow().isEmpty())
    }

    // ── fetchFolderlessLists ──────────────────────────────────────────────────

    @Test
    fun fetchFolderlessLists_parsesLists() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"lists": [{"id": "l1", "name": "Backlog"}, {"id": "l2", "name": "Icebox"}]}""",
            ),
        )

        val lists = service.fetchFolderlessLists("sp1").getOrThrow()

        assertEquals(2, lists.size)
        assertEquals("l1", lists[0].id)
        assertEquals("Icebox", lists[1].name)
    }

    // ── fetchSpaceViews / fetchFolderViews / fetchListViews ───────────────────

    @Test
    fun fetchSpaceViews_includesRequiredListViewAndFiltersOutNonListViews() {
        mockServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "required_views": {"list": {"id": "rv1", "name": "List", "type": "list"}},
                  "views": [
                    {"id": "v1", "name": "Board", "type": "board"},
                    {"id": "v2", "name": "Gantt", "type": "gantt"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val views = service.fetchSpaceViews("sp1").getOrThrow()

        // Only the required list view — board and gantt are filtered out
        assertEquals(1, views.size)
        assertEquals("rv1", views[0].id)
        assertEquals("list", views[0].type)
    }

    @Test
    fun fetchFolderViews_returnsListTypeViewsOnly() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"views": [{"id": "v1", "name": "List View", "type": "list"}]}""",
            ),
        )

        val views = service.fetchFolderViews("f1").getOrThrow()

        assertEquals(1, views.size)
        assertEquals("v1", views[0].id)
    }

    @Test
    fun fetchListViews_parsesEmptyViews() {
        mockServer.enqueue(MockResponse().setBody("""{"views": []}"""))

        assertTrue(service.fetchListViews("l1").getOrThrow().isEmpty())
    }

    // ── Authorization header ──────────────────────────────────────────────────

    @Test
    fun fetchTasks_sendsAuthorizationHeader() {
        mockServer.enqueue(MockResponse().setBody("""{"tasks": []}"""))

        service.fetchTasks("view-1")

        val request = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected outbound request", request)
        assertEquals("Bearer test-token", request!!.getHeader("Authorization"))
    }

    @Test
    fun fetchWorkspaces_sendsAuthorizationHeader() {
        mockServer.enqueue(MockResponse().setBody("""{"teams": []}"""))

        service.fetchWorkspaces()

        val request = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected outbound request", request)
        assertEquals("Bearer test-token", request!!.getHeader("Authorization"))
    }
}
