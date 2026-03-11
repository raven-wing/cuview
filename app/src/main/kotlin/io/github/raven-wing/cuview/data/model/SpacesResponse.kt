package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpacesResponse(
    @SerialName("spaces") val spaces: List<Space>,
)

@Serializable
data class Space(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
