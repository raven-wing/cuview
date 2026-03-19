package io.github.raven_wing.cuview.data.network

import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUWorkspace
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the ClickUp REST API v2.
 *
 * All methods are synchronous (OkHttp blocking call) and must be called from a background thread.
 * Each returns a [Result] — callers never need to catch exceptions.
 *
 * [baseUrl] is overridable in tests to point at a [okhttp3.mockwebserver.MockWebServer].
 */
class CUViewApiService(
    private val baseUrl: String = "https://api.clickup.com/api/v2",
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> get(url: String, token: String): Result<T> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string()
                    ?: return Result.failure(Exception("Empty response body"))
                Result.success(json.decodeFromString<T>(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchTasks(viewId: String, token: String): Result<List<CUTask>> =
        get<ViewTasksResponse>("$baseUrl/view/$viewId/task", token).map { it.tasks }

    fun fetchTasksByList(listId: String, token: String): Result<List<CUTask>> =
        get<ViewTasksResponse>("$baseUrl/list/$listId/task", token).map { response ->
            // ClickUp multi-list: tasks linked from other lists share this list's view but
            // carry their owning list in task.list.id — keep only native tasks.
            response.tasks.filter { it.list?.id == listId }
        }

    fun fetchWorkspaces(token: String): Result<List<CUWorkspace>> =
        get<WorkspacesResponse>("$baseUrl/team", token).map { it.teams }

    fun fetchSpaces(workspaceId: String, token: String): Result<List<CUSpace>> =
        get<SpacesResponse>("$baseUrl/team/$workspaceId/space", token).map { it.spaces }

    fun fetchFolders(spaceId: String, token: String): Result<List<CUFolder>> =
        get<FoldersResponse>("$baseUrl/space/$spaceId/folder", token).map { it.folders }

    fun fetchFolderlessLists(spaceId: String, token: String): Result<List<CUList>> =
        get<ListsResponse>("$baseUrl/space/$spaceId/list", token).map { it.lists }

    fun fetchSpaceViews(spaceId: String, token: String): Result<List<CUView>> =
        get<ListViewsResponse>("$baseUrl/space/$spaceId/view", token).map { extractListViews(it) }

    fun fetchFolderViews(folderId: String, token: String): Result<List<CUView>> =
        get<ListViewsResponse>("$baseUrl/folder/$folderId/view", token).map { extractListViews(it) }

    fun fetchListViews(listId: String, token: String): Result<List<CUView>> =
        get<ListViewsResponse>("$baseUrl/list/$listId/view", token).map { extractListViews(it) }

    private fun extractListViews(response: ListViewsResponse): List<CUView> =
        listOfNotNull(response.requiredViews?.list) +
            response.views.filter { it.type == "list" }

}
