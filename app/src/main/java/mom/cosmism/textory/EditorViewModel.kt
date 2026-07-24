package mom.cosmism.textory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mom.cosmism.textory.data.DocumentRepository
import mom.cosmism.textory.data.EditorAppearancePreferences
import mom.cosmism.textory.data.EditorDocument
import mom.cosmism.textory.data.ProjectSummary
import mom.cosmism.textory.data.StoredVersion
import mom.cosmism.textory.data.normalizeEditorFontSizeSp
import mom.cosmism.textory.diff.AdaptiveDiffEngine
import mom.cosmism.textory.diff.TextChange

data class EditorUiState(
    val projectId: String? = null,
    val fileName: String = "",
    val savedText: String = "",
    val currentText: String = "",
    val hasSavedBase: Boolean = false,
    val documentUri: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val savedVersionCount: Int = 0,
    val diffSourceText: String = "",
    val changes: List<TextChange> = emptyList(),
    val isComparing: Boolean = false,
    val isSaving: Boolean = false,
    val isProjectLoading: Boolean = true,
    val documentGeneration: Int = 0,
    val notice: UiNotice? = null,
) {
    val hasActiveProject: Boolean get() = projectId != null
    val hasUnsavedChanges: Boolean get() = savedText != currentText
}

data class UiNotice(val id: Long = System.nanoTime(), val text: String)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application)
    private val appearancePreferences = EditorAppearancePreferences(application)
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _editorFontSizeSp = MutableStateFlow(appearancePreferences.readFontSizeSp())
    val editorFontSizeSp: StateFlow<Float> = _editorFontSizeSp.asStateFlow()

    private val _projectCatalog = MutableStateFlow(ProjectCatalogUiState())
    val projectCatalog: StateFlow<ProjectCatalogUiState> = _projectCatalog.asStateFlow()

    private val versionCache = LinkedHashMap<String, String>(VERSION_CACHE_LIMIT, 0.75f, true)
    private val _versionHistory = MutableStateFlow(VersionHistoryUiState())
    val versionHistory: StateFlow<VersionHistoryUiState> = _versionHistory.asStateFlow()

    private var compareJob: Job? = null
    private var persistJob: Job? = null
    private var versionPreparationJob: Job? = null
    private val saveJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { repository.initializeCatalog() }
            _projectCatalog.value = ProjectCatalogUiState(snapshot.projects, isLoading = false)
            val active = snapshot.activeDocument
            if (active == null) clearActiveProject() else activateDocument(active)
        }
    }

    fun openProject(id: String) {
        if (_uiState.value.projectId == id && !_uiState.value.isProjectLoading) return
        flushActiveDraft()
        _uiState.value = _uiState.value.copy(isProjectLoading = true)
        viewModelScope.launch {
            val document = withContext(Dispatchers.IO) {
                repository.setActiveProject(id)
                repository.loadProject(id)
            }
            if (document == null) {
                _uiState.value = _uiState.value.copy(isProjectLoading = false)
                refreshProjectCatalog()
            } else {
                activateDocument(document)
            }
        }
    }

    fun onTextChanged(text: String) {
        val state = _uiState.value
        if (text == state.currentText || state.projectId == null) return
        _uiState.value = state.copy(
            currentText = text,
            updatedAt = System.currentTimeMillis(),
            diffSourceText = "",
            isComparing = state.hasSavedBase && text != state.savedText,
        )
        updateActiveProjectSummary()
        putVersionInCache(CURRENT_VERSION_ID, text)
        publishVersionTexts()
        schedulePersistence()
        scheduleComparison(immediate = false)
    }

    fun setEditorFontSizeSp(value: Float) {
        val normalized = normalizeEditorFontSizeSp(value)
        if (_editorFontSizeSp.value == normalized) return
        _editorFontSizeSp.value = normalized
        appearancePreferences.writeFontSizeSp(normalized)
    }

    fun newDocument() {
        flushActiveDraft()
        _uiState.value = _uiState.value.copy(isProjectLoading = true)
        viewModelScope.launch {
            val document = withContext(Dispatchers.IO) {
                repository.createProject(fileName = DocumentRepository.DEFAULT_FILE_NAME)
            }
            activateDocument(document, versions = emptyList())
            refreshProjectCatalog()
        }
    }

    fun importDocument(fileName: String, text: String, uri: String) {
        flushActiveDraft()
        _uiState.value = _uiState.value.copy(isProjectLoading = true)
        viewModelScope.launch {
            val document = withContext(Dispatchers.IO) {
                repository.createProject(
                    fileName = fileName,
                    savedText = text,
                    currentText = text,
                    documentUri = uri,
                    archiveInitialText = true,
                )
            }
            val versions = withContext(Dispatchers.IO) { repository.listVersions(document.id) }
            activateDocument(document, versions)
            refreshProjectCatalog()
            showNotice("Файл импортирован как новый проект")
        }
    }

    fun renameProject(id: String, name: String) {
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) { repository.renameProject(id, name) }
            if (renamed != null && _uiState.value.projectId == id) {
                _uiState.value = _uiState.value.copy(
                    fileName = renamed.fileName,
                    updatedAt = renamed.updatedAt,
                )
            }
            refreshProjectCatalog()
        }
    }

    fun deleteProject(id: String) {
        val deletingActive = _uiState.value.projectId == id
        if (deletingActive) {
            persistJob?.cancel()
            compareJob?.cancel()
        }
        viewModelScope.launch {
            saveJobs[id]?.join()
            val deleted = withContext(Dispatchers.IO) { repository.deleteProject(id) }
            if (!deleted) return@launch
            val snapshot = withContext(Dispatchers.IO) { repository.initializeCatalog() }
            _projectCatalog.value = ProjectCatalogUiState(snapshot.projects, isLoading = false)
            if (deletingActive) {
                val next = snapshot.activeDocument
                if (next == null) clearActiveProject() else activateDocument(next)
            }
        }
    }

    fun discardProjectChanges(id: String) {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { repository.discardProjectChanges(id) }
            if (restored != null && _uiState.value.projectId == id) activateDocument(restored)
            refreshProjectCatalog()
        }
    }

    fun markSaved(savedText: String, uri: String?, fileName: String? = null) {
        val state = _uiState.value
        val projectId = state.projectId ?: return
        if (state.isSaving) return
        val generation = state.documentGeneration
        _uiState.value = state.copy(isSaving = true)

        val job = viewModelScope.launch {
            versionPreparationJob?.join()
            val archiveResult = withContext(Dispatchers.IO) {
                runCatching { repository.archiveVersion(projectId, savedText) }
            }
            val latest = _uiState.value
            if (latest.projectId != projectId || latest.documentGeneration != generation) {
                withContext(Dispatchers.IO) {
                    repository.loadProject(projectId)?.let { document ->
                        repository.persist(
                            document.copy(
                                savedText = savedText,
                                hasSavedBase = true,
                                documentUri = uri ?: document.documentUri,
                                fileName = fileName ?: document.fileName,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                refreshProjectCatalog()
                return@launch
            }

            val currentStillMatches = latest.currentText == savedText
            _uiState.value = latest.copy(
                fileName = normalizeFileName(fileName ?: latest.fileName),
                savedText = savedText,
                hasSavedBase = true,
                documentUri = uri ?: latest.documentUri,
                updatedAt = System.currentTimeMillis(),
                diffSourceText = if (currentStillMatches) latest.currentText else "",
                changes = emptyList(),
                isComparing = !currentStillMatches,
                isSaving = false,
            )
            persistNow()
            if (!currentStillMatches) scheduleComparison(immediate = true)

            val versions = withContext(Dispatchers.IO) { repository.listVersions(projectId) }
            refreshVersionState(versions)
            updateActiveProjectSummary()
            showNotice(
                if (archiveResult.isSuccess) "Сохранено"
                else "Сохранено, но снимок версии создать не удалось",
            )
        }
        saveJobs[projectId] = job
        job.invokeOnCompletion {
            if (saveJobs[projectId] == job) saveJobs.remove(projectId)
        }
    }

    fun restoreSaved() {
        val state = _uiState.value
        if (state.projectId == null) return
        compareJob?.cancel()
        _uiState.value = state.copy(
            currentText = state.savedText,
            updatedAt = System.currentTimeMillis(),
            diffSourceText = state.savedText,
            changes = emptyList(),
            isComparing = false,
            documentGeneration = state.documentGeneration + 1,
        )
        putVersionInCache(CURRENT_VERSION_ID, state.savedText)
        publishVersionTexts()
        persistNow()
        updateActiveProjectSummary()
        showNotice("Возвращено последнее сохранение")
    }

    fun refreshVersionHistory() {
        val projectId = _uiState.value.projectId ?: return
        putVersionInCache(CURRENT_VERSION_ID, _uiState.value.currentText)
        viewModelScope.launch {
            versionPreparationJob?.join()
            val versions = withContext(Dispatchers.IO) { repository.listVersions(projectId) }
            if (_uiState.value.projectId == projectId) refreshVersionState(versions)
        }
    }

    fun selectVersion(index: Int) {
        val projectId = _uiState.value.projectId ?: return
        val history = _versionHistory.value
        val selectedIndex = index.coerceIn(0, (history.items.size - 1).coerceAtLeast(0))
        val item = history.items.getOrNull(selectedIndex) ?: return
        _versionHistory.value = history.copy(selectedIndex = selectedIndex)
        if (item.id == CURRENT_VERSION_ID) {
            putVersionInCache(CURRENT_VERSION_ID, _uiState.value.currentText)
            publishVersionTexts()
            return
        }
        versionCache[item.id]?.let {
            publishVersionTexts()
            return
        }
        if (item.id in history.loadingIds) return
        _versionHistory.value = _versionHistory.value.copy(
            loadingIds = _versionHistory.value.loadingIds + item.id,
        )
        viewModelScope.launch {
            versionPreparationJob?.join()
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.readVersion(projectId, item.id) }
            }
            if (_uiState.value.projectId != projectId) return@launch
            result.onSuccess { text -> putVersionInCache(item.id, text) }
            _versionHistory.value = _versionHistory.value.copy(
                texts = versionCache.toMap(),
                loadingIds = _versionHistory.value.loadingIds - item.id,
            )
            result.onFailure { showNotice("Не удалось открыть сохранённую версию") }
        }
    }

    fun useSelectedVersionAsDraft() {
        val state = _uiState.value
        if (state.projectId == null) return
        val history = _versionHistory.value
        val item = history.selectedItem ?: return
        if (item.isCurrent) return
        val text = history.selectedText ?: return
        compareJob?.cancel()
        _uiState.value = state.copy(
            currentText = text,
            updatedAt = System.currentTimeMillis(),
            diffSourceText = "",
            changes = emptyList(),
            isComparing = state.hasSavedBase && text != state.savedText,
            documentGeneration = state.documentGeneration + 1,
        )
        putVersionInCache(CURRENT_VERSION_ID, text)
        publishVersionTexts()
        persistNow()
        updateActiveProjectSummary()
        scheduleComparison(immediate = true)
        showNotice("Версия помещена в текущий черновик")
    }

    fun showNotice(text: String) {
        _uiState.value = _uiState.value.copy(notice = UiNotice(text = text))
    }

    fun consumeNotice(id: Long) {
        if (_uiState.value.notice?.id == id) {
            _uiState.value = _uiState.value.copy(notice = null)
        }
    }

    private fun activateDocument(document: EditorDocument, versions: List<StoredVersion>? = null) {
        compareJob?.cancel()
        persistJob?.cancel()
        val generation = _uiState.value.documentGeneration + 1
        _uiState.value = EditorUiState(
            projectId = document.id,
            fileName = document.fileName,
            savedText = document.savedText,
            currentText = document.currentText,
            hasSavedBase = document.hasSavedBase,
            documentUri = document.documentUri,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
            savedVersionCount = versions?.size ?: 0,
            diffSourceText = document.currentText,
            isProjectLoading = false,
            documentGeneration = generation,
        )
        repository.setActiveProject(document.id)
        resetVersionState(document.currentText)
        scheduleComparison(immediate = true)

        if (versions != null) {
            refreshVersionState(versions)
        } else {
            versionPreparationJob = viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) { repository.initializeVersions(document) }
                if (_uiState.value.projectId == document.id) refreshVersionState(loaded)
            }
        }
        updateActiveProjectSummary()
    }

    private fun clearActiveProject() {
        compareJob?.cancel()
        persistJob?.cancel()
        _uiState.value = EditorUiState(
            isProjectLoading = false,
            documentGeneration = _uiState.value.documentGeneration + 1,
        )
        resetVersionState("")
    }

    private fun refreshVersionState(versions: List<StoredVersion>) {
        val oldSelectedId = _versionHistory.value.selectedItem?.id ?: CURRENT_VERSION_ID
        val items = buildList {
            add(VersionItem(CURRENT_VERSION_ID, savedAt = null, isCurrent = true))
            versions.forEach { version ->
                add(VersionItem(version.id, savedAt = version.savedAt, isCurrent = false))
            }
        }
        val selectedIndex = items.indexOfFirst { it.id == oldSelectedId }.coerceAtLeast(0)
        putVersionInCache(CURRENT_VERSION_ID, _uiState.value.currentText)
        _versionHistory.value = _versionHistory.value.copy(
            items = items,
            selectedIndex = selectedIndex,
            texts = versionCache.toMap(),
        )
        _uiState.value = _uiState.value.copy(savedVersionCount = versions.size)
        updateActiveProjectSummary()
        if (_versionHistory.value.selectedText == null) selectVersion(selectedIndex)
    }

    private fun resetVersionState(currentText: String) {
        versionCache.clear()
        versionCache[CURRENT_VERSION_ID] = currentText
        _versionHistory.value = VersionHistoryUiState(
            texts = mapOf(CURRENT_VERSION_ID to currentText),
        )
    }

    private fun putVersionInCache(id: String, text: String) {
        versionCache[id] = text
        while (versionCache.size > VERSION_CACHE_LIMIT) {
            val removable = versionCache.keys.firstOrNull { it != CURRENT_VERSION_ID } ?: break
            versionCache.remove(removable)
        }
    }

    private fun publishVersionTexts() {
        _versionHistory.value = _versionHistory.value.copy(texts = versionCache.toMap())
    }

    private fun scheduleComparison(immediate: Boolean) {
        compareJob?.cancel()
        val state = _uiState.value
        val projectId = state.projectId ?: return
        val saved = state.savedText
        val current = state.currentText
        if (!state.hasSavedBase) {
            _uiState.value = state.copy(
                diffSourceText = current,
                changes = emptyList(),
                isComparing = false,
            )
            return
        }
        if (saved == current) {
            _uiState.value = state.copy(
                diffSourceText = current,
                changes = emptyList(),
                isComparing = false,
            )
            return
        }
        compareJob = viewModelScope.launch {
            if (!immediate) delay(COMPARE_DEBOUNCE_MS)
            val snapshot = withContext(Dispatchers.Default) {
                AdaptiveDiffEngine.calculate(saved, current)
            }
            val latest = _uiState.value
            if (latest.projectId == projectId &&
                latest.savedText == saved &&
                latest.currentText == snapshot.sourceText
            ) {
                _uiState.value = latest.copy(
                    diffSourceText = snapshot.sourceText,
                    changes = snapshot.changes,
                    isComparing = false,
                )
            }
        }
    }

    private fun schedulePersistence() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistNow()
        }
    }

    private fun flushActiveDraft() {
        persistJob?.cancel()
        persistNow()
    }

    private fun persistNow() {
        val state = _uiState.value
        val projectId = state.projectId ?: return
        repository.persist(
            EditorDocument(
                id = projectId,
                fileName = state.fileName,
                savedText = state.savedText,
                currentText = state.currentText,
                hasSavedBase = state.hasSavedBase,
                documentUri = state.documentUri,
                createdAt = state.createdAt,
                updatedAt = state.updatedAt,
            ),
        )
    }

    private suspend fun refreshProjectCatalog() {
        val projects = withContext(Dispatchers.IO) { repository.listProjects() }
        _projectCatalog.value = ProjectCatalogUiState(projects, isLoading = false)
    }

    private fun updateActiveProjectSummary() {
        val state = _uiState.value
        val projectId = state.projectId ?: return
        val summary = ProjectSummary(
            id = projectId,
            fileName = state.fileName,
            hasUnsavedChanges = state.hasUnsavedChanges,
            savedVersionCount = state.savedVersionCount,
            updatedAt = state.updatedAt,
        )
        val projects = _projectCatalog.value.projects
            .filterNot { it.id == projectId }
            .plus(summary)
            .sortedByDescending(ProjectSummary::updatedAt)
        _projectCatalog.value = _projectCatalog.value.copy(projects = projects)
    }

    private fun normalizeFileName(name: String): String =
        name.trim().ifEmpty { DocumentRepository.DEFAULT_FILE_NAME }.let {
            if (it.endsWith(".md", ignoreCase = true)) it else "$it.md"
        }

    companion object {
        private const val COMPARE_DEBOUNCE_MS = 70L
        private const val PERSIST_DEBOUNCE_MS = 220L
        private const val VERSION_CACHE_LIMIT = 4
    }
}
