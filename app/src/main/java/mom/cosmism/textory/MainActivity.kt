package mom.cosmism.textory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mom.cosmism.textory.data.AppTheme
import mom.cosmism.textory.data.AppThemePreferences
import mom.cosmism.textory.ui.TextoryApp
import mom.cosmism.textory.ui.theme.TextoryTheme
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDocument(uri)
    }

    private val exportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) exportDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val editorFontSizeSp by viewModel.editorFontSizeSp.collectAsStateWithLifecycle()
            val catalog by viewModel.projectCatalog.collectAsStateWithLifecycle()
            val versionHistory by viewModel.versionHistory.collectAsStateWithLifecycle()
            val updateState by updateViewModel.state.collectAsStateWithLifecycle()
            val themePreferences = remember { AppThemePreferences(applicationContext) }
            var appTheme by remember { mutableStateOf(themePreferences.load()) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    val lightSystemBars = appTheme == AppTheme.LIGHT
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
            TextoryTheme(theme = appTheme) {
                TextoryApp(
                    state = state,
                    editorFontSizeSp = editorFontSizeSp,
                    catalog = catalog,
                    versionHistory = versionHistory,
                    updateState = updateState,
                    appTheme = appTheme,
                    onAppThemeChanged = { theme ->
                        appTheme = theme
                        themePreferences.save(theme)
                    },
                    onOpenProject = viewModel::openProject,
                    onTextChanged = viewModel::onTextChanged,
                    onEditorFontSizeChanged = viewModel::setEditorFontSizeSp,
                    onSave = ::requestSave,
                    onExportDocument = {
                        exportDocument.launch(normalizedFileName(state.fileName))
                    },
                    onNewProject = viewModel::newDocument,
                    onImportDocument = {
                        openDocument.launch(arrayOf("text/markdown", "text/plain", "text/*"))
                    },
                    onRenameProject = viewModel::renameProject,
                    onDeleteProject = viewModel::deleteProject,
                    onDiscardChanges = viewModel::discardProjectChanges,
                    onCheckForUpdates = updateViewModel::checkForUpdates,
                    onDownloadUpdate = updateViewModel::downloadUpdate,
                    onInstallUpdate = updateViewModel::installUpdate,
                    onCancelUpdate = updateViewModel::cancelOperation,
                    onDismissUpdate = updateViewModel::dismiss,
                    onRefreshVersionHistory = viewModel::refreshVersionHistory,
                    onSelectVersion = viewModel::selectVersion,
                    onUseSelectedVersion = viewModel::useSelectedVersionAsDraft,
                    onNoticeConsumed = viewModel::consumeNotice,
                )
            }
        }
    }

    private fun requestSave() {
        val state = viewModel.uiState.value
        val existingUri = state.documentUri?.let(Uri::parse)
        if (existingUri == null) {
            viewModel.markSaved(savedText = state.currentText, uri = null)
        } else {
            saveDocument(existingUri)
        }
    }

    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                withContext(Dispatchers.IO) {
                    val name = queryDisplayName(uri) ?: "Импорт.md"
                    val text = readBoundedUtf8(uri)
                    name to text
                }
            }.onSuccess { (name, text) ->
                viewModel.importDocument(name, text, uri.toString())
            }.onFailure {
                viewModel.showNotice("Не удалось открыть Markdown-файл")
            }
        }
    }

    private fun saveDocument(uri: Uri) {
        val text = viewModel.uiState.value.currentText
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { writeDocument(uri, text) }
            }.onSuccess {
                viewModel.markSaved(
                    savedText = text,
                    uri = null,
                    fileName = null,
                )
            }.onFailure {
                viewModel.showNotice("Не удалось сохранить файл")
            }
        }
    }

    private fun exportDocument(uri: Uri) {
        val text = viewModel.uiState.value.currentText
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { writeDocument(uri, text) }
            }.onSuccess {
                viewModel.showNotice("Копия экспортирована")
            }.onFailure {
                viewModel.showNotice("Не удалось экспортировать файл")
            }
        }
    }

    private fun writeDocument(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(text)
        } ?: error("Document provider returned no output stream")
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        }

    private fun readBoundedUtf8(uri: Uri): String {
        val output = ByteArrayOutputStream()
        contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                require(total <= MAX_DOCUMENT_BYTES) { "Document is too large" }
                output.write(buffer, 0, read)
            }
        } ?: error("Document provider returned no input stream")
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private fun normalizedFileName(fileName: String): String =
        fileName.trim().ifEmpty { "Без названия.md" }.let {
            if (it.endsWith(".md", ignoreCase = true)) it else "$it.md"
        }

    companion object {
        private const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
    }
}
