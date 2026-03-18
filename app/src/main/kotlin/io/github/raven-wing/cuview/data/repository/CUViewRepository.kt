package io.github.raven_wing.cuview.data.repository

import android.content.Context
import android.util.Log
import io.github.raven_wing.cuview.BuildConfig
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.Folder
import io.github.raven_wing.cuview.data.model.ListViewsResponse
import io.github.raven_wing.cuview.data.model.Space
import io.github.raven_wing.cuview.data.model.Task
import io.github.raven_wing.cuview.data.network.CUViewApiService
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.data.storage.TaskStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Views, folders, and folderless lists belonging to a single ClickUp space. */
data class SpaceContents(
    val spaceViews: List<CUView>,
    val folders: List<Folder>,
    val folderlessLists: List<CUList>,
)

/**
 * Business logic layer between the UI / WorkManager and the network/storage layers.
 *
 * - [syncTasks] is called by [io.github.raven_wing.cuview.worker.TaskSyncWorker] on every
 *   periodic or immediate sync. It writes results to [io.github.raven_wing.cuview.data.storage.TaskStorage]
 *   and preserves stale tasks on failure.
 * - [fetchSpaces], [fetchSpaceContents], [fetchFolderViews], [fetchListViews] drive the
 *   browse tree in [io.github.raven_wing.cuview.ui.config.WidgetConfigActivity].
 * - [previewTasks] is used by the config screen to show a task count before the user saves.
 *
 * When [io.github.raven_wing.cuview.BuildConfig.USE_MOCK_API] is true (debug builds),
 * all methods return data from [FakeData] without hitting the network.
 */
class CUViewRepository(
    private val context: Context,
    private val securePreferences: SecurePreferences = SecurePreferences(context),
    private val apiService: CUViewApiService = CUViewApiService(),
) {

    /** Returns true if the widget has both an API token and a configured target. */
    fun isConfigured(widgetId: Int): Boolean =
        securePreferences.apiToken != null && securePreferences.viewId(widgetId) != null

    companion object {
        /** Convenience overload for callers that only need to check without a subsequent sync. */
        fun isConfigured(context: Context, widgetId: Int): Boolean =
            CUViewRepository(context).isConfigured(widgetId)
    }

    suspend fun syncTasks(widgetId: Int): Result<Unit> {
        val token = securePreferences.apiToken
        val targetId = securePreferences.viewId(widgetId)
        if (BuildConfig.DEBUG) Log.d("CUViewRepo", "syncTasks: widgetId=$widgetId token=${if (token != null) "set" else "null"} target=${if (targetId != null) "set" else "null"}")
        token ?: return Result.failure(Exception("API token not configured"))
        targetId ?: return Result.failure(Exception("View not configured"))

        if (BuildConfig.USE_MOCK_API) {
            val isListTarget = securePreferences.isListTarget(widgetId)
            val mockTasks = FakeData.tasksForTarget(targetId, isListTarget)
            if (BuildConfig.DEBUG) Log.d("CUViewRepo", "syncTasks mock: target=$targetId isList=$isListTarget -> ${mockTasks.size} tasks")
            val taskStorage = TaskStorage(context, widgetId)
            taskStorage.saveTasks(mockTasks)
            taskStorage.clearError()
            return Result.success(Unit)
        }

        val taskStorage = TaskStorage(context, widgetId)
        val result = if (securePreferences.isListTarget(widgetId)) {
            apiService.fetchTasksByList(targetId, token)
        } else {
            apiService.fetchTasks(targetId, token)
        }
        return result.fold(
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

    suspend fun previewTasks(targetId: String, isListTarget: Boolean, token: String): Result<List<Task>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.tasksForTarget(targetId, isListTarget))
            if (isListTarget) {
                apiService.fetchTasksByList(targetId, token)
            } else {
                apiService.fetchTasks(targetId, token)
            }
        }

    suspend fun fetchSpaces(token: String): Result<List<Space>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.spaces)
            try {
                // NOTE: Only the first workspace is used. Users with multiple workspaces
                // will only see spaces from their first workspace.
                val workspace = apiService.fetchWorkspaces(token)
                    .getOrThrow()
                    .teams
                    .firstOrNull()
                    ?: return@withContext Result.failure(Exception("No workspaces found"))
                apiService.fetchSpaces(workspace.id, token).map { it.spaces }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchSpaceContents(spaceId: String, token: String): Result<SpaceContents> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(
                FakeData.spaceContents[spaceId] ?: SpaceContents(emptyList(), emptyList(), emptyList())
            )
            try {
                coroutineScope {
                    val viewsDeferred = async {
                        extractListViews(apiService.fetchSpaceViews(spaceId, token).getOrThrow())
                    }
                    val foldersDeferred = async {
                        apiService.fetchFolders(spaceId, token).getOrThrow().folders
                    }
                    val listsDeferred = async {
                        apiService.fetchFolderlessLists(spaceId, token).getOrThrow().lists
                    }
                    Result.success(
                        SpaceContents(
                            spaceViews = viewsDeferred.await(),
                            folders = foldersDeferred.await(),
                            folderlessLists = listsDeferred.await(),
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchFolderViews(folderId: String, token: String): Result<List<CUView>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.folderViews[folderId] ?: emptyList())
            try {
                Result.success(extractListViews(apiService.fetchFolderViews(folderId, token).getOrThrow()))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchListViews(listId: String, token: String): Result<List<CUView>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.listViews[listId] ?: emptyList())
            try {
                Result.success(extractListViews(apiService.fetchListViews(listId, token).getOrThrow()))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // Strip URLs (which may contain sensitive path segments like view/list IDs) from error
    // messages before they are stored in TaskStorage and rendered on the home screen widget.
    private fun sanitizeErrorMessage(message: String?): String {
        if (message == null) return "Unknown error"
        return message
            .replace(Regex("https?://\\S+"), "[url]")
            .take(200)
    }

    private fun extractListViews(response: ListViewsResponse): List<CUView> =
        listOfNotNull(response.requiredViews?.list) +
            response.views.filter { it.type == "list" }
}
