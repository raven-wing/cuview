package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ViewTasksResponse(
    @SerialName("tasks") val tasks: List<CUTask>,
)
