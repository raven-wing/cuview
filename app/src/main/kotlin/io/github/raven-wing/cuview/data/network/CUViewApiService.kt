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
    val token: String,
    private val baseUrl: String = "https://api.clickup.com/api/v2",
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            )
        }
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> get(path: String): Result<T> {
        val request = Request.Builder().url("$baseUrl$path").build()
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

    fun fetchTasks(viewId: String): Result<List<CUTask>> =
        get<ViewTasksResponse>("/view/$viewId/task").map { it.tasks }

    fun fetchTasksByList(listId: String): Result<List<CUTask>> =
        get<ViewTasksResponse>("/list/$listId/task").map { response ->
            // ClickUp multi-list: tasks linked from other lists share this list's view but
            // carry their owning list in task.list.id — keep only native tasks.
            response.tasks.filter { it.list?.id == listId }
        }

    fun fetchWorkspaces(): Result<List<CUWorkspace>> =
        get<WorkspacesResponse>("/team").map { it.teams }

    fun fetchSpaces(workspaceId: String): Result<List<CUSpace>> =
        get<SpacesResponse>("/team/$workspaceId/space").map { it.spaces }

    fun fetchFolders(spaceId: String): Result<List<CUFolder>> =
        get<FoldersResponse>("/space/$spaceId/folder").map { it.folders }

    fun fetchFolderlessLists(spaceId: String): Result<List<CUList>> =
        get<ListsResponse>("/space/$spaceId/list").map { it.lists }

    fun fetchSpaceViews(spaceId: String): Result<List<CUView>> =
        get<ListViewsResponse>("/space/$spaceId/view").map { extractListViews(it) }

    fun fetchFolderViews(folderId: String): Result<List<CUView>> =
        get<ListViewsResponse>("/folder/$folderId/view").map { extractListViews(it) }

    fun fetchListViews(listId: String): Result<List<CUView>> =
        get<ListViewsResponse>("/list/$listId/view").map { extractListViews(it) }

    private fun extractListViews(response: ListViewsResponse): List<CUView> =
        (listOfNotNull(response.requiredViews?.list) +
            response.views.filter { it.type == "list" })
            .distinctBy { it.id }
}
