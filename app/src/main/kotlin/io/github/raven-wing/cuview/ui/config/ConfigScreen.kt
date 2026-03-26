package io.github.raven_wing.cuview.ui.config

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.raven_wing.cuview.R
import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.TasksSource
import io.github.raven_wing.cuview.data.repository.SpaceContents
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.ui.main.MainActivity
import io.github.raven_wing.cuview.widget.WidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Repository callbacks ──────────────────────────────────────────────────────

/** Wraps all repository calls needed by the config screen, keeping [ConfigScreen]'s parameter list flat. */
internal data class RepositoryCallbacks(
    val fetchSpaces: suspend (token: String) -> Result<List<CUSpace>>,
    val fetchSpaceContents: suspend (spaceId: String, token: String) -> Result<SpaceContents>,
    val fetchFolderViews: suspend (folderId: String, token: String) -> Result<List<CUView>>,
    val fetchListViews: suspend (listId: String, token: String) -> Result<List<CUView>>,
    val previewViewTasksSource: suspend (tasksSourceId: String, token: String) -> Result<List<CUTask>>,
    val previewListTasksSource: suspend (tasksSourceId: String, token: String) -> Result<List<CUTask>>,
)

// ── Navigation ────────────────────────────────────────────────────────────────

internal sealed class NavLevel {
    data object Root : NavLevel()
    data class SpaceContents(val space: CUSpace) : NavLevel()
    data class FolderContents(val space: CUSpace, val folder: CUFolder) : NavLevel()
    data class ListSelection(val space: CUSpace, val folder: CUFolder?, val list: CUList) : NavLevel()
}

// ── Async loading state ───────────────────────────────────────────────────────

internal sealed class LoadState<out T> {
    data object Loading : LoadState<Nothing>()
    data class Success<T>(val data: T) : LoadState<T>()
    data class Failure(val message: String) : LoadState<Nothing>()
}

private fun <T> Result<T>.toLoadState(fallbackError: String = "Failed to load"): LoadState<T> =
    fold(
        onSuccess = { LoadState.Success(it) },
        onFailure = { LoadState.Failure(it.message ?: fallbackError) },
    )

// ── Selection + preview ───────────────────────────────────────────────────────

internal sealed class PreviewState {
    data object Idle : PreviewState()
    data object Loading : PreviewState()
    data class Loaded(val tasks: List<CUTask>) : PreviewState()
    data class Error(val message: String) : PreviewState()
}

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
internal fun ConfigScreen(
    tokenCheckSignal: Int,
    initialThemeId: String?,
    initialTasksSource: TasksSource? = null,
    initialTasks: List<CUTask>? = null,
    onSave: (tasksSource: TasksSource, previewTasks: List<CUTask>, theme: WidgetTheme) -> Unit,
    callbacks: RepositoryCallbacks,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiToken by remember { mutableStateOf<String?>(null) }
    var isCheckingToken by remember { mutableStateOf(true) }

    LaunchedEffect(tokenCheckSignal) {
        val token = withContext(Dispatchers.IO) { SecurePreferences(context).apiToken }
        apiToken = token
        isCheckingToken = false
    }

    var navLevel by remember { mutableStateOf<NavLevel>(NavLevel.Root) }
    var spacesState by remember { mutableStateOf<LoadState<List<CUSpace>>?>(null) }
    var spaceContentsState by remember { mutableStateOf<LoadState<SpaceContents>?>(null) }
    var folderViewsState by remember { mutableStateOf<LoadState<List<CUView>>?>(null) }
    var listViewsState by remember { mutableStateOf<LoadState<List<CUView>>?>(null) }
    var selectedTasksSource by remember { mutableStateOf(initialTasksSource) }
    // Pre-populate with cached tasks when editing so Save is immediately available.
    var previewState by remember {
        mutableStateOf<PreviewState>(
            if (initialTasks != null) PreviewState.Loaded(initialTasks) else PreviewState.Idle
        )
    }
    var selectedTheme by remember { mutableStateOf(WidgetTheme.fromId(initialThemeId)) }

    // Keyed on both selectedTasksSource and apiToken: when editing, the tasks source is pre-set
    // but the token loads asynchronously, so we need to re-run the preview once the token is available.
    LaunchedEffect(selectedTasksSource, apiToken) {
        val tasksSource = selectedTasksSource ?: run { previewState = PreviewState.Idle; return@LaunchedEffect }
        val token = apiToken ?: return@LaunchedEffect
        previewState = PreviewState.Loading
        val result = when (tasksSource) {
            is TasksSource.List -> callbacks.previewListTasksSource(tasksSource.id, token.trim())
            is TasksSource.View -> callbacks.previewViewTasksSource(tasksSource.id, token.trim())
        }
        previewState = result.fold(
            onSuccess = { PreviewState.Loaded(it) },
            onFailure = { PreviewState.Error(it.message ?: "Failed to load tasks") },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.config_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        when {
            isCheckingToken -> {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            apiToken == null -> {
                Text(
                    text = stringResource(R.string.config_not_connected_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.config_open_app_button))
                }
            }

            else -> {
                val token = apiToken!!.trim()
                Spacer(Modifier.height(12.dp))

                when (val level = navLevel) {
                    is NavLevel.Root -> SpaceListLevel(
                        spacesState = spacesState,
                        onBrowse = {
                            spacesState = LoadState.Loading
                            spaceContentsState = null
                            folderViewsState = null
                            listViewsState = null
                            selectedTasksSource = null
                            scope.launch {
                                spacesState = callbacks.fetchSpaces(token).toLoadState("Failed to load spaces")
                            }
                        },
                        onSpaceClick = { space ->
                            navLevel = NavLevel.SpaceContents(space)
                            spaceContentsState = LoadState.Loading
                            folderViewsState = null
                            listViewsState = null
                            selectedTasksSource = null
                            scope.launch {
                                val result = callbacks.fetchSpaceContents(space.id, token)
                                if (navLevel != NavLevel.SpaceContents(space)) return@launch
                                spaceContentsState = result.toLoadState("Failed to load space")
                            }
                        },
                    )

                    is NavLevel.SpaceContents -> SpaceContentsLevel(
                        space = level.space,
                        contentsState = spaceContentsState,
                        selectedTasksSource = selectedTasksSource,
                        onBack = {
                            navLevel = NavLevel.Root
                            spaceContentsState = null
                            selectedTasksSource = null
                        },
                        onViewClick = { view ->
                            previewState = PreviewState.Loading
                            selectedTasksSource = TasksSource.View(view.id, buildBreadcrumb(level.space.name, view.name))
                        },
                        onFolderClick = { folder ->
                            navLevel = NavLevel.FolderContents(level.space, folder)
                            folderViewsState = LoadState.Loading
                            listViewsState = null
                            selectedTasksSource = null
                            scope.launch {
                                val result = callbacks.fetchFolderViews(folder.id, token)
                                if (navLevel != NavLevel.FolderContents(level.space, folder)) return@launch
                                folderViewsState = result.toLoadState("Failed to load folder views")
                            }
                        },
                        onListClick = { list ->
                            navLevel = NavLevel.ListSelection(level.space, null, list)
                            listViewsState = LoadState.Loading
                            selectedTasksSource = null
                            scope.launch {
                                val result = callbacks.fetchListViews(list.id, token)
                                if (navLevel != NavLevel.ListSelection(level.space, null, list)) return@launch
                                listViewsState = result.toLoadState("Failed to load list views")
                            }
                        },
                    )

                    is NavLevel.FolderContents -> FolderContentsLevel(
                        space = level.space,
                        folder = level.folder,
                        viewsState = folderViewsState,
                        selectedTasksSource = selectedTasksSource,
                        onBack = {
                            navLevel = NavLevel.SpaceContents(level.space)
                            folderViewsState = null
                            selectedTasksSource = null
                        },
                        onViewClick = { view ->
                            previewState = PreviewState.Loading
                            selectedTasksSource = TasksSource.View(view.id, buildBreadcrumb(level.space.name, level.folder.name, view.name))
                        },
                        onListClick = { list ->
                            navLevel = NavLevel.ListSelection(level.space, level.folder, list)
                            listViewsState = LoadState.Loading
                            selectedTasksSource = null
                            scope.launch {
                                val result = callbacks.fetchListViews(list.id, token)
                                if (navLevel != NavLevel.ListSelection(level.space, level.folder, list)) return@launch
                                listViewsState = result.toLoadState("Failed to load list views")
                            }
                        },
                    )

                    is NavLevel.ListSelection -> ListSelectionLevel(
                        space = level.space,
                        folder = level.folder,
                        list = level.list,
                        viewsState = listViewsState,
                        selectedTasksSource = selectedTasksSource,
                        onBack = {
                            navLevel = if (level.folder != null)
                                NavLevel.FolderContents(level.space, level.folder)
                            else
                                NavLevel.SpaceContents(level.space)
                            listViewsState = null
                            selectedTasksSource = null
                        },
                        onTasksSourceClick = {
                            previewState = PreviewState.Loading
                            selectedTasksSource = it
                        },
                    )
                }

                PreviewSection(selectedTasksSource, previewState)

                Spacer(Modifier.height(20.dp))
                ThemePickerSection(
                    selectedTheme = selectedTheme,
                    onThemeChange = { selectedTheme = it },
                )

                Spacer(Modifier.height(24.dp))
                val isEditing = initialTasksSource != null
                Button(
                    onClick = {
                        val tasksSource = selectedTasksSource ?: return@Button
                        val tasks = (previewState as? PreviewState.Loaded)?.tasks ?: initialTasks ?: emptyList()
                        onSave(tasksSource, tasks, selectedTheme)
                    },
                    // When editing, allow save with cached tasks even while the preview refreshes.
                    enabled = selectedTasksSource != null &&
                        (previewState is PreviewState.Loaded || (isEditing && previewState is PreviewState.Loading)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (isEditing) R.string.config_update_button else R.string.config_save_button))
                }
            }
        }
    }
}
