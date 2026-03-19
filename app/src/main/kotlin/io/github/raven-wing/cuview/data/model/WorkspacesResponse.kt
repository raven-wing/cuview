package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkspacesResponse(
    @SerialName("teams") val teams: List<CUWorkspace>,
)

@Serializable
data class CUWorkspace(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
