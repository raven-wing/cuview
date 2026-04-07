package io.github.raven_wing.cuview.data.network

import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUWorkspace
import io.github.raven_wing.cuview.data.model.TasksSource

/** Contract for the ClickUp API — implemented by [CUViewApiService] in production and a fake in debug builds. */
interface CUViewApi {
    fun fetchTasks(viewId: String): Result<List<CUTask>>
    fun fetchTasksByList(listId: String): Result<List<CUTask>>
    fun fetchTasksBySource(source: TasksSource): Result<List<CUTask>> = when (source) {
        is TasksSource.List -> fetchTasksByList(source.id)
        is TasksSource.View -> fetchTasks(source.id)
    }
    fun fetchWorkspaces(): Result<List<CUWorkspace>>
    fun fetchSpaces(workspaceId: String): Result<List<CUSpace>>
    fun fetchFolders(spaceId: String): Result<List<CUFolder>>
    fun fetchFolderlessLists(spaceId: String): Result<List<CUList>>
    fun fetchSpaceViews(spaceId: String): Result<List<CUView>>
    fun fetchFolderViews(folderId: String): Result<List<CUView>>
    fun fetchListViews(listId: String): Result<List<CUView>>
}
