package io.github.raven_wing.cuview.data.repository

import android.content.Context
import android.util.Log
import io.github.raven_wing.cuview.BuildConfig
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.network.CUViewApi
import io.github.raven_wing.cuview.data.network.CUViewApiService
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Views, folders, and folderless lists belonging to a single ClickUp space. */
data class SpaceContents(
    val spaceViews: List<CUView>,
    val folders: List<CUFolder>,
    val folderlessLists: List<CUList>,
)

/**
 * Business logic layer between the UI / WorkManager and the network/storage layers.
 *
 * - [syncTasks] is called by [io.github.raven_wing.cuview.worker.TaskSyncWorker] on every
 *   periodic or triggered sync. It writes results to [io.github.raven_wing.cuview.data.storage.TaskStorage]
 *   and preserves cached tasks on failure.
 * - [fetchSpaces], [fetchSpaceContents], [fetchFolderViews], [fetchListViews] drive the
 *   browse tree in [io.github.raven_wing.cuview.ui.config.WidgetConfigActivity].
 * - [previewViewTasks] / [previewListTasks] are used by the config screen to show a task count before the user saves.
 *
 * When [io.github.raven_wing.cuview.BuildConfig.USE_MOCK_API] is true (debug builds),
 * all API calls are routed through [FakeApiService] without hitting the network.
 */
class CUViewRepository(
    private val context: Context,
    private val securePreferences: SecurePreferences = SecurePreferences(context),
    private val apiBaseUrl: String = "https://api.clickup.com/api/v2",
) {

    private fun api(token: String): CUViewApi =
        if (BuildConfig.USE_MOCK_API) FakeApiService else CUViewApiService(token = token, baseUrl = apiBaseUrl)

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Returns true if the widget has both an API token and a configured tasks source. */
    fun isConfigured(widgetId: Int): Boolean =
        !securePreferences.apiToken.isNullOrBlank() && !securePreferences.viewId(widgetId).isNullOrBlank()

    suspend fun syncTasks(widgetId: Int): Result<Unit> {
        val token = securePreferences.apiToken
        val tasksSourceId = securePreferences.viewId(widgetId)
        if (BuildConfig.DEBUG) Log.d("CUViewRepo", "syncTasks: widgetId=$widgetId token=${if (token != null) "set" else "null"} tasksSourceId=${if (tasksSourceId != null) "set" else "null"}")
        token ?: return Result.failure(Exception("API token not configured"))
        tasksSourceId ?: return Result.failure(Exception("Tasks source not configured"))

        val taskStorage = TaskStorage(context, widgetId)
        val fetch = if (securePreferences.isListTasksSource(widgetId))
            api(token).fetchTasksByList(tasksSourceId)
        else
            api(token).fetchTasks(tasksSourceId)

        return fetch.fold(
            onSuccess = { tasks ->
                taskStorage.saveTasks(tasks)
                taskStorage.clearError()
                Result.success(Unit)
            },
            onFailure = { error ->
                taskStorage.saveError(sanitizeErrorMessage(error.message))
                Result.failure(error)
            },
        )
    }

    suspend fun previewViewTasks(tasksSourceId: String, token: String): Result<List<CUTask>> =
        withContext(Dispatchers.IO) { api(token).fetchTasks(tasksSourceId) }

    suspend fun previewListTasks(tasksSourceId: String, token: String): Result<List<CUTask>> =
        withContext(Dispatchers.IO) { api(token).fetchTasksByList(tasksSourceId) }

    suspend fun fetchSpaces(token: String): Result<List<CUSpace>> = apiCall {
        // NOTE: Only the first workspace is used. Users with multiple workspaces
        // will only see spaces from their first workspace.
        val workspace = api(token).fetchWorkspaces().getOrThrow().firstOrNull()
            ?: throw Exception("No workspaces found")
        api(token).fetchSpaces(workspace.id).getOrThrow()
    }

    suspend fun fetchSpaceContents(spaceId: String, token: String): Result<SpaceContents> = apiCall {
        val api = api(token)
        coroutineScope {
            val viewsDeferred = async { api.fetchSpaceViews(spaceId).getOrThrow() }
            val foldersDeferred = async { api.fetchFolders(spaceId).getOrThrow() }
            val listsDeferred = async { api.fetchFolderlessLists(spaceId).getOrThrow() }
            SpaceContents(
                spaceViews = viewsDeferred.await(),
                folders = foldersDeferred.await(),
                folderlessLists = listsDeferred.await(),
            )
        }
    }

    suspend fun fetchFolderViews(folderId: String, token: String): Result<List<CUView>> =
        apiCall { api(token).fetchFolderViews(folderId).getOrThrow() }

    suspend fun fetchListViews(listId: String, token: String): Result<List<CUView>> =
        apiCall { api(token).fetchListViews(listId).getOrThrow() }

    // Strip URLs (which may contain sensitive path segments like view/list IDs) from error
    // messages before they are stored in TaskStorage and rendered on the home screen widget.
    private fun sanitizeErrorMessage(message: String?): String =
        (message ?: "Unknown error").replace(Regex("https?://\\S+"), "[url]").take(200)
}
