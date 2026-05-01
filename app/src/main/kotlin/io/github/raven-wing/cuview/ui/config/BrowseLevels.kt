package io.github.raven_wing.cuview.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.raven_wing.cuview.R
import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.TasksSource
import io.github.raven_wing.cuview.data.repository.SpaceContents

// ── Level 1: space list ───────────────────────────────────────────────────────

@Composable
internal fun SpaceListLevel(
    spacesState: LoadState<List<CUSpace>>?,
    onRetry: () -> Unit,
    onSpaceClick: (CUSpace) -> Unit,
) {
    when {
        spacesState == null || spacesState is LoadState.Loading -> LoadingRow()
        spacesState is LoadState.Failure -> {
            Spacer(Modifier.height(8.dp))
            Text(spacesState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.config_retry_button))
            }
        }
        spacesState is LoadState.Success -> {
            SectionLabel("Spaces")
            spacesState.data.forEach { space ->
                BrowseItem(text = space.name, selected = false, drillDown = true, onClick = { onSpaceClick(space) })
            }
        }
    }
}

// ── Level 2: space contents ───────────────────────────────────────────────────

@Composable
internal fun SpaceContentsLevel(
    space: CUSpace,
    contentsState: LoadState<SpaceContents>?,
    selectedTasksSource: TasksSource?,
    crumbs: List<Crumb>,
    onViewClick: (CUView) -> Unit,
    onFolderClick: (CUFolder) -> Unit,
    onListClick: (CUList) -> Unit,
) {
    BreadcrumbBar(crumbs = crumbs)

    when (contentsState) {
        is LoadState.Loading -> LoadingRow()
        is LoadState.Failure -> {
            Spacer(Modifier.height(4.dp))
            Text(contentsState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        is LoadState.Success -> {
            val contents = contentsState.data
            if (contents.spaceViews.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Views")
                contents.spaceViews.forEach { view ->
                    BrowseItem(
                        text = view.name,
                        selected = selectedTasksSource is TasksSource.View && selectedTasksSource.id == view.id,
                        drillDown = false,
                        onClick = { onViewClick(view) },
                    )
                }
            }
            if (contents.folders.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Folders")
                contents.folders.forEach { folder ->
                    BrowseItem(text = folder.name, selected = false, drillDown = true, onClick = { onFolderClick(folder) })
                }
            }
            if (contents.folderlessLists.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Lists")
                contents.folderlessLists.forEach { list ->
                    BrowseItem(text = list.name, selected = false, drillDown = true, onClick = { onListClick(list) })
                }
            }
        }
        null -> Unit
    }
}

// ── Level 3: folder contents ──────────────────────────────────────────────────

@Composable
internal fun FolderContentsLevel(
    space: CUSpace,
    folder: CUFolder,
    viewsState: LoadState<List<CUView>>?,
    selectedTasksSource: TasksSource?,
    crumbs: List<Crumb>,
    onViewClick: (CUView) -> Unit,
    onListClick: (CUList) -> Unit,
) {
    BreadcrumbBar(crumbs = crumbs)

    when (viewsState) {
        is LoadState.Loading -> LoadingRow()
        is LoadState.Failure -> {
            Spacer(Modifier.height(4.dp))
            Text(viewsState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        is LoadState.Success -> {
            if (viewsState.data.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Views")
                viewsState.data.forEach { view ->
                    BrowseItem(
                        text = view.name,
                        selected = selectedTasksSource is TasksSource.View && selectedTasksSource.id == view.id,
                        drillDown = false,
                        onClick = { onViewClick(view) },
                    )
                }
            }
        }
        null -> Unit
    }
    if (folder.lists.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Lists")
        folder.lists.forEach { list ->
            BrowseItem(text = list.name, selected = false, drillDown = true, onClick = { onListClick(list) })
        }
    }
}

// ── Level 4: list selection ───────────────────────────────────────────────────

@Composable
internal fun ListSelectionLevel(
    space: CUSpace,
    folder: CUFolder?,
    list: CUList,
    viewsState: LoadState<List<CUView>>?,
    selectedTasksSource: TasksSource?,
    crumbs: List<Crumb>,
    onTasksSourceClick: (TasksSource) -> Unit,
) {
    BreadcrumbBar(crumbs = crumbs)

    val sourceParts = listOfNotNull(space.name, folder?.name, list.name)
    Spacer(Modifier.height(8.dp))
    SectionLabel("Select in ${list.name}")

    // The list tasks source is always shown immediately so that a tap arriving via
    // propagation from the folder level can auto-select it before views finish loading.
    BrowseItem(
        text = list.name,
        selected = selectedTasksSource is TasksSource.List && selectedTasksSource.id == list.id,
        drillDown = false,
        onClick = {
            onTasksSourceClick(TasksSource.List(list.id, buildBreadcrumb(*sourceParts.toTypedArray())))
        },
    )

    when (viewsState) {
        is LoadState.Loading -> LoadingRow()
        is LoadState.Failure -> {
            Spacer(Modifier.height(4.dp))
            Text(viewsState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        is LoadState.Success -> viewsState.data.forEach { view ->
            BrowseItem(
                text = view.name,
                selected = selectedTasksSource is TasksSource.View && selectedTasksSource.id == view.id,
                drillDown = false,
                onClick = {
                    onTasksSourceClick(TasksSource.View(view.id, buildBreadcrumb(*(sourceParts + view.name).toTypedArray())))
                },
            )
        }
        null -> Unit
    }
}

// ── Preview section ───────────────────────────────────────────────────────────

@Composable
internal fun PreviewSection(selectedTasksSource: TasksSource?, previewState: PreviewState) {
    if (selectedTasksSource == null) return

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(
        text = selectedTasksSource.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))

    when (previewState) {
        is PreviewState.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        is PreviewState.Loaded -> {
            Text(
                text = pluralStringResource(R.plurals.config_preview_header, previewState.tasks.size, previewState.tasks.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previewState.tasks.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.config_preview_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                previewState.tasks.take(10).forEach { task ->
                    Spacer(Modifier.height(4.dp))
                    Text("• ${task.name}", style = MaterialTheme.typography.bodyMedium)
                }
                if (previewState.tasks.size > 10) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.config_preview_more, previewState.tasks.size - 10),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is PreviewState.Error -> Text(
            previewState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        PreviewState.Idle -> Unit
    }
}
