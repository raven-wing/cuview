package io.github.raven_wing.cuview.data.repository

import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUWorkspace
import io.github.raven_wing.cuview.data.network.CUViewApi

internal object FakeApiService : CUViewApi {
    override fun fetchTasks(viewId: String): Result<List<CUTask>> =
        Result.success(FakeData.tasksForView(viewId))

    override fun fetchTasksByList(listId: String): Result<List<CUTask>> =
        Result.success(FakeData.tasksForList(listId))

    override fun fetchWorkspaces(): Result<List<CUWorkspace>> =
        Result.success(listOf(CUWorkspace("mock_workspace", "Mock Workspace")))

    override fun fetchSpaces(workspaceId: String): Result<List<CUSpace>> =
        Result.success(FakeData.spaces)

    override fun fetchFolders(spaceId: String): Result<List<CUFolder>> =
        Result.success(FakeData.spaceContents[spaceId]?.folders ?: emptyList())

    override fun fetchFolderlessLists(spaceId: String): Result<List<CUList>> =
        Result.success(FakeData.spaceContents[spaceId]?.folderlessLists ?: emptyList())

    override fun fetchSpaceViews(spaceId: String): Result<List<CUView>> =
        Result.success(FakeData.spaceContents[spaceId]?.spaceViews ?: emptyList())

    override fun fetchFolderViews(folderId: String): Result<List<CUView>> =
        Result.success(FakeData.folderViews[folderId] ?: emptyList())

    override fun fetchListViews(listId: String): Result<List<CUView>> =
        Result.success(FakeData.listViews[listId] ?: emptyList())
}
