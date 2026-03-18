package io.github.raven_wing.cuview.data.network

import io.github.raven_wing.cuview.data.model.FoldersResponse
import io.github.raven_wing.cuview.data.model.ListViewsResponse
import io.github.raven_wing.cuview.data.model.ListsResponse
import io.github.raven_wing.cuview.data.model.SpacesResponse
import io.github.raven_wing.cuview.data.model.Task
import io.github.raven_wing.cuview.data.model.ViewTasksResponse
import io.github.raven_wing.cuview.data.model.WorkspacesResponse
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
            .header("Authorization", token)
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

    fun fetchTasks(viewId: String, token: String): Result<List<Task>> =
        get<ViewTasksResponse>("$baseUrl/view/$viewId/task", token).map { it.tasks }

    fun fetchWorkspaces(token: String): Result<WorkspacesResponse> =
        get("$baseUrl/team", token)

    fun fetchSpaces(workspaceId: String, token: String): Result<SpacesResponse> =
        get("$baseUrl/team/$workspaceId/space", token)

    fun fetchFolders(spaceId: String, token: String): Result<FoldersResponse> =
        get("$baseUrl/space/$spaceId/folder", token)

    fun fetchFolderlessLists(spaceId: String, token: String): Result<ListsResponse> =
        get("$baseUrl/space/$spaceId/list", token)

    fun fetchSpaceViews(spaceId: String, token: String): Result<ListViewsResponse> =
        get("$baseUrl/space/$spaceId/view", token)

    fun fetchFolderViews(folderId: String, token: String): Result<ListViewsResponse> =
        get("$baseUrl/folder/$folderId/view", token)

    fun fetchListViews(listId: String, token: String): Result<ListViewsResponse> =
        get("$baseUrl/list/$listId/view", token)

    fun fetchTasksByList(listId: String, token: String): Result<List<Task>> =
        get<ViewTasksResponse>("$baseUrl/list/$listId/task", token).map { response ->
            // ClickUp multi-list: tasks linked from other lists share this list's view but
            // carry their owning list in task.list.id — keep only native tasks.
            response.tasks.filter { it.list?.id == listId }
        }
}
