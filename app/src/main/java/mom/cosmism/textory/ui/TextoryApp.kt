package mom.cosmism.textory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import mom.cosmism.textory.EditorUiState
import mom.cosmism.textory.ProjectCatalogUiState
import mom.cosmism.textory.UpdateUiState
import mom.cosmism.textory.VersionHistoryUiState
import mom.cosmism.textory.data.AppTheme

private enum class AppDestination { HOME, EDITOR, VERSIONS }

@Composable
internal fun TextoryApp(
    state: EditorUiState,
    editorFontSizeSp: Float,
    catalog: ProjectCatalogUiState,
    versionHistory: VersionHistoryUiState,
    updateState: UpdateUiState,
    appTheme: AppTheme,
    onAppThemeChanged: (AppTheme) -> Unit,
    onOpenProject: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onEditorFontSizeChanged: (Float) -> Unit,
    onSave: () -> Unit,
    onExportDocument: () -> Unit,
    onNewProject: (String) -> Unit,
    onImportDocument: () -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDiscardChanges: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onRefreshVersionHistory: () -> Unit,
    onSelectVersion: (Int) -> Unit,
    onUseSelectedVersion: () -> Unit,
    onNoticeConsumed: (Long) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var pendingDestination by rememberSaveable { mutableStateOf<AppDestination?>(null) }
    var pendingGeneration by rememberSaveable { mutableIntStateOf(state.documentGeneration) }
    var startEditorInEdit by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = destination != AppDestination.HOME) {
        destination = AppDestination.HOME
    }

    LaunchedEffect(state.documentGeneration, state.isProjectLoading) {
        val target = pendingDestination
        if (target != null &&
            !state.isProjectLoading &&
            state.hasActiveProject &&
            state.documentGeneration != pendingGeneration
        ) {
            pendingDestination = null
            if (target == AppDestination.VERSIONS) onRefreshVersionHistory()
            destination = target
        }
    }

    fun waitForProject(target: AppDestination, editMode: Boolean, action: () -> Unit) {
        pendingGeneration = state.documentGeneration
        pendingDestination = target
        startEditorInEdit = editMode
        action()
    }

    fun openProject(id: String, target: AppDestination) {
        if (state.projectId == id && !state.isProjectLoading) {
            pendingDestination = null
            startEditorInEdit = false
            if (target == AppDestination.VERSIONS) onRefreshVersionHistory()
            destination = target
        } else {
            waitForProject(target, editMode = false) { onOpenProject(id) }
        }
    }

    when (destination) {
        AppDestination.HOME -> HomeScreen(
            catalog = catalog,
            updateState = updateState,
            appTheme = appTheme,
            onAppThemeChanged = onAppThemeChanged,
            onOpenProject = { id -> openProject(id, AppDestination.EDITOR) },
            onNewProject = { name ->
                waitForProject(AppDestination.EDITOR, editMode = true) { onNewProject(name) }
            },
            onImportDocument = {
                waitForProject(AppDestination.EDITOR, editMode = false, action = onImportDocument)
            },
            onOpenVersions = { id -> openProject(id, AppDestination.VERSIONS) },
            onRenameProject = { id, name ->
                pendingDestination = null
                onRenameProject(id, name)
            },
            onDeleteProject = { id ->
                pendingDestination = null
                onDeleteProject(id)
            },
            onDiscardChanges = { id ->
                pendingDestination = null
                onDiscardChanges(id)
            },
            onCheckForUpdates = onCheckForUpdates,
        )

        AppDestination.EDITOR -> EditorScreen(
            state = state,
            startInEditMode = startEditorInEdit,
            editorFontSizeSp = editorFontSizeSp,
            onBack = { destination = AppDestination.HOME },
            onTextChanged = onTextChanged,
            onEditorFontSizeChanged = onEditorFontSizeChanged,
            onSave = onSave,
            onExportDocument = onExportDocument,
            onNoticeConsumed = onNoticeConsumed,
        )

        AppDestination.VERSIONS -> VersionHistoryScreen(
            state = versionHistory,
            fontSizeSp = editorFontSizeSp,
            onBack = { destination = AppDestination.HOME },
            onSelectVersion = onSelectVersion,
            onUseSelectedVersion = {
                onUseSelectedVersion()
                startEditorInEdit = false
                destination = AppDestination.EDITOR
            },
        )
    }

    UpdateDialog(
        state = updateState,
        onDownload = onDownloadUpdate,
        onInstall = onInstallUpdate,
        onCancel = onCancelUpdate,
        onDismiss = onDismissUpdate,
    )
}
