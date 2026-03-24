package io.github.raven_wing.cuview.data.repository

import android.content.Context
import android.util.Log
import io.github.raven_wing.cuview.BuildConfig
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
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
    private val apiBaseUrl: String = "https://api.clickup.com/api/v2",
) {

    private fun api(token: String) = CUViewApiService(token = token, baseUrl = apiBaseUrl)

    /** Returns true if the widget has both an API token and a configured tasks source. */
    fun isConfigured(widgetId: Int): Boolean =
        !securePreferences.apiToken.isNullOrBlank() && !securePreferences.viewId(widgetId).isNullOrBlank()

    suspend fun syncTasks(widgetId: Int): Result<Unit> {
        val token = securePreferences.apiToken
        val viewId = securePreferences.viewId(widgetId)
        if (BuildConfig.DEBUG) Log.d("CUViewRepo", "syncTasks: widgetId=$widgetId token=${if (token != null) "set" else "null"} viewId=${if (viewId != null) "set" else "null"}")
        token ?: return Result.failure(Exception("API token not configured"))
        viewId ?: return Result.failure(Exception("View not configured"))

        if (BuildConfig.USE_MOCK_API) {
            val isListTasksSource = securePreferences.isListTasksSource(widgetId)
            val mockTasks = FakeData.tasksForTasksSource(viewId, isListTasksSource)
            if (BuildConfig.DEBUG) Log.d("CUViewRepo", "syncTasks mock: isList=$isListTasksSource -> ${mockTasks.size} tasks")
            val taskStorage = TaskStorage(context, widgetId)
            taskStorage.saveTasks(mockTasks)
            taskStorage.clearError()
            return Result.success(Unit)
        }

        val taskStorage = TaskStorage(context, widgetId)
        val result = if (securePreferences.isListTasksSource(widgetId)) {
            api(token).fetchTasksByList(viewId)
        } else {
            api(token).fetchTasks(viewId)
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

    suspend fun previewTasks(viewId: String, isListTasksSource: Boolean, token: String): Result<List<CUTask>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.tasksForTasksSource(viewId, isListTasksSource))
            if (isListTasksSource) api(token).fetchTasksByList(viewId) else api(token).fetchTasks(viewId)
        }

    suspend fun fetchSpaces(token: String): Result<List<CUSpace>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.spaces)
            try {
                // NOTE: Only the first workspace is used. Users with multiple workspaces
                // will only see spaces from their first workspace.
                val workspace = api(token).fetchWorkspaces()
                    .getOrThrow()
                    .firstOrNull()
                    ?: return@withContext Result.failure(Exception("No workspaces found"))
                api(token).fetchSpaces(workspace.id)
            } catch (e: CancellationException) {
                throw e
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
                val api = api(token)
                coroutineScope {
                    val viewsDeferred = async { api.fetchSpaceViews(spaceId).getOrThrow() }
                    val foldersDeferred = async { api.fetchFolders(spaceId).getOrThrow() }
                    val listsDeferred = async { api.fetchFolderlessLists(spaceId).getOrThrow() }
                    Result.success(
                        SpaceContents(
                            spaceViews = viewsDeferred.await(),
                            folders = foldersDeferred.await(),
                            folderlessLists = listsDeferred.await(),
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchFolderViews(folderId: String, token: String): Result<List<CUView>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.folderViews[folderId] ?: emptyList())
            try {
                Result.success(api(token).fetchFolderViews(folderId).getOrThrow())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchListViews(listId: String, token: String): Result<List<CUView>> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.USE_MOCK_API) return@withContext Result.success(FakeData.listViews[listId] ?: emptyList())
            try {
                Result.success(api(token).fetchListViews(listId).getOrThrow())
            } catch (e: CancellationException) {
                throw e
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
}
