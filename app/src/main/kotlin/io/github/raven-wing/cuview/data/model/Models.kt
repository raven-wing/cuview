package io.github.raven_wing.cuview.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class TasksSource(val id: String, val label: String) {
    class View(id: String, label: String = "") : TasksSource(id, label)
    class List(id: String, label: String = "") : TasksSource(id, label)

    fun withLabel(label: String): TasksSource = when (this) {
        is View -> View(id, label)
        is List -> List(id, label)
    }
}

@Serializable
data class CUWorkspace(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

@Serializable
data class CUSpace(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

@Serializable
data class CUFolder(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("lists") val lists: List<CUList>,
)

@Serializable
data class CUList(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

@Serializable
data class CUView(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String = "",
)

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
