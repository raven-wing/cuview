package io.github.raven_wing.cuview.data.network

import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUWorkspace
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal data class ViewTasksResponse(
    @SerialName("tasks") val tasks: List<CUTask>,
)

@Serializable internal data class WorkspacesResponse(
    @SerialName("teams") val teams: List<CUWorkspace>,
)

@Serializable internal data class SpacesResponse(
    @SerialName("spaces") val spaces: List<CUSpace>,
)

@Serializable internal data class FoldersResponse(
    @SerialName("folders") val folders: List<CUFolder>,
)

@Serializable internal data class ListsResponse(
    @SerialName("lists") val lists: List<CUList>,
)

@Serializable internal data class ListViewsResponse(
    @SerialName("views") val views: List<CUView> = emptyList(),
    @SerialName("required_views") val requiredViews: RequiredViews? = null,
)

// required_views is an object keyed by view type, each value is a view or null
@Serializable internal data class RequiredViews(
    @SerialName("list") val list: CUView? = null,
)
