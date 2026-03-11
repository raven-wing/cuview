package io.github.raven_wing.cuview.data.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Unit tests for the remaining CUViewApiService endpoints (everything except
// fetchTasksByList, which is covered in CUViewApiServiceTest).
class CUViewApiServiceFetchTest {

    private val mockServer = MockWebServer()
    private lateinit var service: CUViewApiService

    @Before
    fun setUp() {
        mockServer.start()
        service = CUViewApiService(baseUrl = "http://${mockServer.hostName}:${mockServer.port}")
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

        val tasks = service.fetchTasks("view-1", "token").getOrThrow()

        assertEquals(2, tasks.size)
        assertEquals("t1", tasks[0].id)
        assertEquals("Beta", tasks[1].name)
    }

    @Test
    fun fetchTasks_returnsFailureOnHttpError() {
        mockServer.enqueue(MockResponse().setResponseCode(403))

        val result = service.fetchTasks("view-1", "bad-token")

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

        val response = service.fetchWorkspaces("token").getOrThrow()

        assertEquals(2, response.teams.size)
        assertEquals("ws1", response.teams[0].id)
        assertEquals("Umbrella", response.teams[1].name)
    }

    @Test
    fun fetchWorkspaces_parsesEmptyTeamList() {
        mockServer.enqueue(MockResponse().setBody("""{"teams": []}"""))

        val response = service.fetchWorkspaces("token").getOrThrow()

        assertTrue(response.teams.isEmpty())
    }

    // ── fetchSpaces ───────────────────────────────────────────────────────────

    @Test
    fun fetchSpaces_parsesSpaceList() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"spaces": [{"id": "sp1", "name": "Engineering"}, {"id": "sp2", "name": "Marketing"}]}""",
            ),
        )

        val response = service.fetchSpaces("ws1", "token").getOrThrow()

        assertEquals(2, response.spaces.size)
        assertEquals("sp1", response.spaces[0].id)
        assertEquals("Marketing", response.spaces[1].name)
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

        val response = service.fetchFolders("sp1", "token").getOrThrow()

        assertEquals(1, response.folders.size)
        val folder = response.folders[0]
        assertEquals("f1", folder.id)
        assertEquals("Sprint", folder.name)
        assertEquals(2, folder.lists.size)
        assertEquals("l1", folder.lists[0].id)
        assertEquals("Done", folder.lists[1].name)
    }

    @Test
    fun fetchFolders_parsesEmptyFolderList() {
        mockServer.enqueue(MockResponse().setBody("""{"folders": []}"""))

        val response = service.fetchFolders("sp1", "token").getOrThrow()

        assertTrue(response.folders.isEmpty())
    }

    // ── fetchFolderlessLists ──────────────────────────────────────────────────

    @Test
    fun fetchFolderlessLists_parsesLists() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"lists": [{"id": "l1", "name": "Backlog"}, {"id": "l2", "name": "Icebox"}]}""",
            ),
        )

        val response = service.fetchFolderlessLists("sp1", "token").getOrThrow()

        assertEquals(2, response.lists.size)
        assertEquals("l1", response.lists[0].id)
        assertEquals("Icebox", response.lists[1].name)
    }

    // ── fetchSpaceViews / fetchFolderViews / fetchListViews ───────────────────

    @Test
    fun fetchSpaceViews_parsesRequiredViewsAndViews() {
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

        val response = service.fetchSpaceViews("sp1", "token").getOrThrow()

        // required_views.list should be present
        assertEquals("rv1", response.requiredViews?.list?.id)
        // views list should include both additional views
        assertEquals(2, response.views.size)
        assertEquals("board", response.views[0].type)
    }

    @Test
    fun fetchFolderViews_parsesViewsWithNullRequiredViews() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"views": [{"id": "v1", "name": "List View", "type": "list"}]}""",
            ),
        )

        val response = service.fetchFolderViews("f1", "token").getOrThrow()

        assertNull(response.requiredViews)
        assertEquals(1, response.views.size)
        assertEquals("v1", response.views[0].id)
    }

    @Test
    fun fetchListViews_parsesEmptyViews() {
        mockServer.enqueue(MockResponse().setBody("""{"views": []}"""))

        val response = service.fetchListViews("l1", "token").getOrThrow()

        assertTrue(response.views.isEmpty())
    }

    // ── Authorization header ──────────────────────────────────────────────────

    @Test
    fun fetchTasks_sendsAuthorizationHeader() {
        mockServer.enqueue(MockResponse().setBody("""{"tasks": []}"""))

        service.fetchTasks("view-1", "pk_my_secret_token")

        val request = mockServer.takeRequest()
        assertEquals("pk_my_secret_token", request.getHeader("Authorization"))
    }

    @Test
    fun fetchWorkspaces_sendsAuthorizationHeader() {
        mockServer.enqueue(MockResponse().setBody("""{"teams": []}"""))

        service.fetchWorkspaces("pk_another_token")

        val request = mockServer.takeRequest()
        assertEquals("pk_another_token", request.getHeader("Authorization"))
    }
}

private fun assertNull(actual: Any?) = assertTrue("Expected null but was: $actual", actual == null)
