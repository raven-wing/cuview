package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoldersResponse(
    @SerialName("folders") val folders: List<CUFolder>,
)

@Serializable
data class CUFolder(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("lists") val lists: List<CUList>,
)
