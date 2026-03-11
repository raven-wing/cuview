package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListViewsResponse(
    @SerialName("views") val views: List<CUView> = emptyList(),
    @SerialName("required_views") val requiredViews: RequiredViews? = null,
)

// required_views is an object keyed by view type, each value is a view or null
@Serializable
data class RequiredViews(
    @SerialName("list") val list: CUView? = null,
)

@Serializable
data class CUView(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String = "",
)
