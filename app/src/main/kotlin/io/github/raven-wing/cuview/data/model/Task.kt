package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskListRef(
    @SerialName("id") val id: String,
)

@Serializable
data class CUTask(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("list") val list: TaskListRef? = null,
)
