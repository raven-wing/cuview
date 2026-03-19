package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpacesResponse(
    @SerialName("spaces") val spaces: List<CUSpace>,
)

@Serializable
data class CUSpace(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
