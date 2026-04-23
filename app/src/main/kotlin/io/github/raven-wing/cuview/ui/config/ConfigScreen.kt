package io.github.raven_wing.cuview.ui.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.raven_wing.cuview.BuildConfig
import io.github.raven_wing.cuview.R
import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.TasksSource
import io.github.raven_wing.cuview.data.repository.SpaceContents
import io.github.raven_wing.cuview.data.storage.SecurePreferences
import io.github.raven_wing.cuview.widget.WidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ── Repository callbacks ──────────────────────────────────────────────────────

/** Wraps all repository calls needed by the config screen, keeping [ConfigScreen]'s parameter list flat. */
internal data class RepositoryCallbacks(
    val fetchSpaces: suspend (token: String) -> Result<List<CUSpace>>,
    val fetchSpaceContents: suspend (spaceId: String, token: String) -> Result<SpaceContents>,
    val fetchFolderViews: suspend (folderId: String, token: String) -> Result<List<CUView>>,
    val fetchListViews: suspend (listId: String, token: String) -> Result<List<CUView>>,
    val previewTasksSource: suspend (source: TasksSource, token: String) -> Result<List<CUTask>>,
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
    tokenState: TokenState,
    pendingOAuthResult: OAuthResult?,
    onOAuthResultConsumed: () -> Unit,
    onTokenChanged: (TokenState) -> Unit,
    initialThemeId: String?,
    initialTasksSource: TasksSource? = null,
    initialTasks: List<CUTask>? = null,
    onSave: (tasksSource: TasksSource, previewTasks: List<CUTask>, theme: WidgetTheme) -> Unit,
    callbacks: RepositoryCallbacks,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var oauthError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingOAuthResult) {
        val result = pendingOAuthResult ?: return@LaunchedEffect
        // Token is already saved by WidgetConfigActivity.handleOAuthIntent / handleOAuthCallback.
        // This effect only needs to update UI state.
        when (result) {
            is OAuthResult.Success -> { onTokenChanged(TokenState.Token(result.token)); oauthError = null }
            is OAuthResult.Failure -> oauthError = result.error
        }
        onOAuthResultConsumed()
    }

    LaunchedEffect(tokenState) {
        if (tokenState is TokenState.None) {
            navLevel = NavLevel.Root
            spacesState = null
            spaceContentsState = null
            folderViewsState = null
            listViewsState = null
            selectedTasksSource = null
            previewState = PreviewState.Idle
        }
    }

    // Keyed on both selectedTasksSource and tokenState: when editing, the tasks source is pre-set
    // but the token loads asynchronously, so we need to re-run the preview once the token is available.
    LaunchedEffect(selectedTasksSource, tokenState) {
        if (selectedTasksSource == null) {
            previewState = PreviewState.Idle
            return@LaunchedEffect
        }
        val tasksSource = selectedTasksSource ?: return@LaunchedEffect
        val token = (tokenState as? TokenState.Token)?.value ?: return@LaunchedEffect
        previewState = PreviewState.Loading
        val result = callbacks.previewTasksSource(tasksSource, token)
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

        when (tokenState) {
            is TokenState.Loading -> {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            else -> {
                ConnectSection(
                    apiToken = if (tokenState is TokenState.Token) tokenState.value else null,
                    oauthError = oauthError,
                    onConnect = {
                        val state = UUID.randomUUID().toString()
                        // Persist state before launching the CCT so handleOAuthCallback /
                        // handleOAuthIntent can validate it even if the process is recreated.
                        context.getSharedPreferences(WidgetConfigActivity.PREFS_OAUTH_STATE, Context.MODE_PRIVATE)
                            .edit().putString(WidgetConfigActivity.KEY_PENDING_STATE, state).apply()
                        val authUrl = if (BuildConfig.USE_MOCK_API) {
                            // Point at the local mock OAuth server (e2e/mock_oauth_server.py)
                            // running on the host machine. This exercises the full Chrome CCT →
                            // intent:// → handleOAuthCallback path, including Chrome's task-switching
                            // behaviour, without real ClickUp credentials.
                            // Port must match MOCK_OAUTH_PORT in mock_oauth_server.py.
                            "http://10.0.2.2:8765/?state=$state"
                        } else {
                            val redirectUri = Uri.encode(BuildConfig.CLOUDFLARE_WORKER_URL)
                            "https://app.clickup.com/api" +
                                "?client_id=${BuildConfig.CLICKUP_CLIENT_ID}" +
                                "&redirect_uri=$redirectUri" +
                                "&state=$state"
                        }
                        // FLAG_ACTIVITY_NEW_TASK opens Chrome in its own task so that
                        // WidgetConfigActivity remains in the launcher's task. The OAuth
                        // callback (cuview:// intent) therefore lands in a fresh instance
                        // handled by handleOAuthCallback, which calls moveTaskToFront to
                        // bring the original instance back to the foreground.
                        val cct = CustomTabsIntent.Builder().build()
                        cct.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        cct.launchUrl(context, Uri.parse(authUrl))
                    },
                    onDisconnect = {
                        scope.launch(Dispatchers.IO) { SecurePreferences(context).apiToken = null }
                        onTokenChanged(TokenState.None)
                    },
                )

                if (tokenState is TokenState.Token) {
                    val token = tokenState.value
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
}

// ── Connect / disconnect section ───────────────────────────────────────────────

@Composable
private fun ConnectSection(
    apiToken: String?,
    oauthError: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (apiToken == null) {
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.config_connect_button))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.config_connected_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.config_disconnect_button),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onDisconnect)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
    if (oauthError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = oauthError,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
