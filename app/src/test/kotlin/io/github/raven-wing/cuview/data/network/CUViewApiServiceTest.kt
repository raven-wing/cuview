package io.github.raven_wing.cuview.data.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CUViewApiServiceTest {

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

    // Regression test: ClickUp returns tasks from multiple lists when querying /list/{id}/task
    // because tasks can be linked into a list from elsewhere (multi-list assignment).
    // We must filter to only return tasks whose list.id matches the requested list.
    @Test
    fun fetchTasksByList_returnsOnlyTasksBelongingToRequestedList() {
        mockServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "tasks": [
                    {"id": "t1", "name": "Own task 1",     "list": {"id": "list-A", "name": "List A"}},
                    {"id": "t2", "name": "Linked from B",  "list": {"id": "list-B", "name": "List B"}},
                    {"id": "t3", "name": "Own task 2",     "list": {"id": "list-A", "name": "List A"}},
                    {"id": "t4", "name": "Linked from C",  "list": {"id": "list-C", "name": "List C"}}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val tasks = service.fetchTasksByList("list-A").getOrThrow()

        assertEquals(2, tasks.size)
        assertEquals("t1", tasks[0].id)
        assertEquals("t3", tasks[1].id)
    }

    @Test
    fun fetchTasksByList_excludesTasksWithMissingListField() {
        mockServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "tasks": [
                    {"id": "t1", "name": "Has list",    "list": {"id": "list-A"}},
                    {"id": "t2", "name": "No list field"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val tasks = service.fetchTasksByList("list-A").getOrThrow()

        assertEquals(1, tasks.size)
        assertEquals("t1", tasks[0].id)
    }

    @Test
    fun fetchTasksByList_returnsEmptyListWhenNoTasksBelongToRequestedList() {
        mockServer.enqueue(
            MockResponse().setBody(
                """{"tasks": [{"id": "t1", "name": "Linked", "list": {"id": "list-other"}}]}""",
            ),
        )

        val tasks = service.fetchTasksByList("list-A").getOrThrow()

        assertTrue(tasks.isEmpty())
    }

    @Test
    fun fetchTasksByList_returnsFailureOnHttpError() {
        mockServer.enqueue(MockResponse().setResponseCode(401))

        val result = service.fetchTasksByList("list-A")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }
}
