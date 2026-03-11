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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.raven_wing.cuview.R
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.Folder
import io.github.raven_wing.cuview.data.model.Space
import io.github.raven_wing.cuview.data.model.Task
import io.github.raven_wing.cuview.data.repository.SpaceContents

// ── Level 1: space list ───────────────────────────────────────────────────────

@Composable
internal fun SpaceListLevel(
    spacesState: LoadState<List<Space>>?,
    onBrowse: () -> Unit,
    onSpaceClick: (Space) -> Unit,
) {
    Button(
        onClick = onBrowse,
        enabled = spacesState !is LoadState.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (spacesState is LoadState.Loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.config_browse_button))
        }
    }

    if (spacesState is LoadState.Failure) {
        Spacer(Modifier.height(8.dp))
        Text(spacesState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (spacesState is LoadState.Success && spacesState.data.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("Spaces")
        spacesState.data.forEach { space ->
            BrowseItem(text = space.name, selected = false, drillDown = true, onClick = { onSpaceClick(space) })
        }
    }
}

// ── Level 2: space contents ───────────────────────────────────────────────────

@Composable
internal fun SpaceContentsLevel(
    space: Space,
    contentsState: LoadState<SpaceContents>?,
    selectedTarget: SelectedTarget?,
    onBack: () -> Unit,
    onViewClick: (CUView) -> Unit,
    onFolderClick: (Folder) -> Unit,
    onListClick: (CUList) -> Unit,
) {
    BackRow(label = "Spaces", onBack = onBack)

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
                        selected = selectedTarget?.id == view.id && selectedTarget.isListTarget == false,
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
    space: Space,
    folder: Folder,
    viewsState: LoadState<List<CUView>>?,
    selectedTarget: SelectedTarget?,
    onBack: () -> Unit,
    onViewClick: (CUView) -> Unit,
    onListClick: (CUList) -> Unit,
) {
    BackRow(label = space.name, onBack = onBack)

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
                        selected = selectedTarget?.id == view.id && selectedTarget.isListTarget == false,
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
    space: Space,
    folder: Folder?,
    list: CUList,
    viewsState: LoadState<List<CUView>>?,
    selectedTarget: SelectedTarget?,
    onBack: () -> Unit,
    onTargetClick: (SelectedTarget) -> Unit,
) {
    BackRow(label = folder?.name ?: space.name, onBack = onBack)

    val folderPart = folder?.let { " \u203a ${it.name}" } ?: ""
    Spacer(Modifier.height(8.dp))
    SectionLabel("Select in ${list.name}")

    // The list target is always shown immediately so that a tap arriving via
    // propagation from the folder level can auto-select it before views finish loading.
    BrowseItem(
        text = list.name,
        selected = selectedTarget?.id == list.id && selectedTarget.isListTarget,
        drillDown = false,
        onClick = {
            onTargetClick(SelectedTarget(
                id = list.id,
                label = "${space.name}$folderPart \u203a ${list.name}",
                isListTarget = true,
            ))
        },
    )

    when (viewsState) {
        is LoadState.Loading -> LoadingRow()
        is LoadState.Success -> viewsState.data.forEach { view ->
            BrowseItem(
                text = view.name,
                selected = selectedTarget?.id == view.id && selectedTarget.isListTarget == false,
                drillDown = false,
                onClick = {
                    onTargetClick(SelectedTarget(
                        id = view.id,
                        label = "${space.name}$folderPart \u203a ${list.name} \u203a ${view.name}",
                        isListTarget = false,
                    ))
                },
            )
        }
        else -> Unit
    }
}

// ── Preview section ───────────────────────────────────────────────────────────

@Composable
internal fun PreviewSection(selectedTarget: SelectedTarget?, previewState: PreviewState) {
    if (selectedTarget == null) return

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(
        text = selectedTarget.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))

    when (previewState) {
        is PreviewState.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        is PreviewState.Loaded -> {
            Text(
                text = stringResource(R.string.config_preview_header, previewState.tasks.size),
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
