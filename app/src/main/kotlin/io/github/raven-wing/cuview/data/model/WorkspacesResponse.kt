package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkspacesResponse(
    @SerialName("teams") val teams: List<Workspace>,
)

@Serializable
data class Workspace(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
