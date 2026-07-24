package mom.cosmism.textory.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File
import java.util.UUID

class DocumentRepository(private val context: Context) {
    private val legacyPreferences = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
    private val catalogPreferences = context.getSharedPreferences(CATALOG_PREFERENCES, Context.MODE_PRIVATE)
    private val projectsRoot = File(context.filesDir, "projects")

    fun initializeCatalog(): ProjectCatalogSnapshot {
        var ids = projectIds()
        if (ids.isEmpty() && !catalogPreferences.getBoolean(KEY_CATALOG_INITIALIZED, false)) {
            val migrated = migrateLegacyProject()
            ids = setOf(migrated.id)
            catalogPreferences.edit {
                putStringSet(KEY_PROJECT_IDS, ids)
                putString(KEY_ACTIVE_PROJECT_ID, migrated.id)
                putBoolean(KEY_CATALOG_INITIALIZED, true)
            }
        }

        val projects = listProjects()
        val requestedActive = catalogPreferences.getString(KEY_ACTIVE_PROJECT_ID, null)
        val activeId = requestedActive?.takeIf(ids::contains) ?: projects.firstOrNull()?.id
        if (activeId != requestedActive) setActiveProject(activeId)
        return ProjectCatalogSnapshot(
            projects = projects,
            activeDocument = activeId?.let(::loadProject),
        )
    }

    fun listProjects(): List<ProjectSummary> = projectIds()
        .mapNotNull { id ->
            val preferences = projectPreferences(id)
            if (!preferences.contains(KEY_FILE_NAME)) return@mapNotNull null
            ProjectSummary(
                id = id,
                fileName = preferences.getString(KEY_FILE_NAME, DEFAULT_FILE_NAME).orEmpty(),
                hasUnsavedChanges = preferences.getBoolean(KEY_HAS_UNSAVED_CHANGES, false),
                savedVersionCount = preferences.getInt(KEY_VERSION_COUNT, 0),
                updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L),
            )
        }
        .sortedByDescending(ProjectSummary::updatedAt)

    fun loadProject(id: String): EditorDocument? {
        if (id !in projectIds()) return null
        val preferences = projectPreferences(id)
        if (!preferences.contains(KEY_FILE_NAME)) return null
        val savedText = preferences.getString(KEY_SAVED_TEXT, "").orEmpty()
        val documentUri = preferences.getString(KEY_DOCUMENT_URI, null)
        return EditorDocument(
            id = id,
            fileName = preferences.getString(KEY_FILE_NAME, DEFAULT_FILE_NAME).orEmpty(),
            savedText = savedText,
            currentText = preferences.getString(KEY_CURRENT_TEXT, "").orEmpty(),
            hasSavedBase = if (preferences.contains(KEY_HAS_SAVED_BASE)) {
                preferences.getBoolean(KEY_HAS_SAVED_BASE, false)
            } else {
                savedText.isNotEmpty() || documentUri != null || preferences.getInt(KEY_VERSION_COUNT, 0) > 0
            },
            documentUri = documentUri,
            createdAt = preferences.getLong(KEY_CREATED_AT, 0L),
            updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    fun createProject(
        fileName: String,
        savedText: String = "",
        currentText: String = "",
        documentUri: String? = null,
        archiveInitialText: Boolean = false,
        hasSavedBase: Boolean = archiveInitialText || savedText.isNotEmpty(),
    ): EditorDocument {
        val now = System.currentTimeMillis()
        val document = EditorDocument(
            id = UUID.randomUUID().toString(),
            fileName = uniqueFileName(fileName),
            savedText = savedText,
            currentText = currentText,
            hasSavedBase = hasSavedBase,
            documentUri = documentUri,
            createdAt = now,
            updatedAt = now,
        )
        addProjectId(document.id)
        persist(document)
        if (archiveInitialText) archiveVersion(document.id, savedText)
        setActiveProject(document.id)
        return document
    }

    fun persist(document: EditorDocument) {
        requireValidProjectId(document.id)
        addProjectId(document.id)
        projectPreferences(document.id).edit {
            putString(KEY_FILE_NAME, document.fileName)
            putString(KEY_SAVED_TEXT, document.savedText)
            putString(KEY_CURRENT_TEXT, document.currentText)
            putBoolean(KEY_HAS_SAVED_BASE, document.hasSavedBase)
            putLong(KEY_CREATED_AT, document.createdAt)
            putLong(KEY_UPDATED_AT, document.updatedAt)
            putBoolean(KEY_HAS_UNSAVED_CHANGES, document.savedText != document.currentText)
            if (document.documentUri == null) remove(KEY_DOCUMENT_URI)
            else putString(KEY_DOCUMENT_URI, document.documentUri)
        }
    }

    fun renameProject(id: String, requestedName: String): EditorDocument? {
        val document = loadProject(id) ?: return null
        val renamed = document.copy(
            fileName = uniqueFileName(requestedName, excludingProjectId = id),
            updatedAt = System.currentTimeMillis(),
        )
        persist(renamed)
        return renamed
    }

    fun discardProjectChanges(id: String): EditorDocument? {
        val document = loadProject(id) ?: return null
        val restored = document.copy(
            currentText = document.savedText,
            updatedAt = System.currentTimeMillis(),
        )
        persist(restored)
        return restored
    }

    fun deleteProject(id: String): Boolean {
        if (id !in projectIds()) return false
        val activeId = catalogPreferences.getString(KEY_ACTIVE_PROJECT_ID, null)
        val nextActiveId = listProjects().firstOrNull { it.id != id }?.id
        projectPreferences(id).edit { clear() }
        projectDirectory(id).deleteRecursively()
        val remaining = projectIds() - id
        catalogPreferences.edit {
            putStringSet(KEY_PROJECT_IDS, remaining)
            if (activeId == id) {
                if (nextActiveId == null) remove(KEY_ACTIVE_PROJECT_ID)
                else putString(KEY_ACTIVE_PROJECT_ID, nextActiveId)
            }
            putBoolean(KEY_CATALOG_INITIALIZED, true)
        }
        return true
    }

    fun setActiveProject(id: String?) {
        catalogPreferences.edit {
            if (id == null) remove(KEY_ACTIVE_PROJECT_ID)
            else putString(KEY_ACTIVE_PROJECT_ID, id)
        }
    }

    fun initializeVersions(document: EditorDocument): List<StoredVersion> {
        val versions = versionArchive(document.id)
        if (versions.list().isEmpty() && document.hasSavedBase) {
            versions.archive(document.savedText)
        }
        return updateVersionCount(document.id, versions.list())
    }

    fun archiveVersion(projectId: String, text: String): StoredVersion {
        val version = versionArchive(projectId).archive(text)
        updateVersionCount(projectId, versionArchive(projectId).list())
        return version
    }

    fun listVersions(projectId: String): List<StoredVersion> =
        updateVersionCount(projectId, versionArchive(projectId).list())

    fun readVersion(projectId: String, id: String): String = versionArchive(projectId).read(id)

    fun uniqueUntitledName(): String = uniqueFileName(DEFAULT_FILE_NAME)

    private fun migrateLegacyProject(): EditorDocument {
        val now = System.currentTimeMillis()
        val document = EditorDocument(
            id = UUID.randomUUID().toString(),
            fileName = if (legacyPreferences.contains(KEY_CURRENT_TEXT)) {
                legacyPreferences.getString(KEY_FILE_NAME, DEFAULT_FILE_NAME).orEmpty()
            } else {
                DemoDocument.FILE_NAME
            },
            savedText = if (legacyPreferences.contains(KEY_CURRENT_TEXT)) {
                legacyPreferences.getString(KEY_SAVED_TEXT, "").orEmpty()
            } else {
                DemoDocument.saved
            },
            currentText = if (legacyPreferences.contains(KEY_CURRENT_TEXT)) {
                legacyPreferences.getString(KEY_CURRENT_TEXT, "").orEmpty()
            } else {
                DemoDocument.current
            },
            hasSavedBase = if (legacyPreferences.contains(KEY_CURRENT_TEXT)) {
                legacyPreferences.getString(KEY_SAVED_TEXT, "").orEmpty().isNotEmpty() ||
                    legacyPreferences.getString(KEY_DOCUMENT_URI, null) != null ||
                    File(context.filesDir, "document_versions").listFiles().orEmpty().isNotEmpty()
            } else {
                true
            },
            documentUri = legacyPreferences.getString(KEY_DOCUMENT_URI, null),
            createdAt = now,
            updatedAt = now,
        )
        addProjectId(document.id)
        persist(document)
        migrateLegacyArchive(
            projectId = document.id,
            savedText = document.savedText,
            previousSavedText = legacyPreferences.getString(KEY_PREVIOUS_SAVED_TEXT, null),
        )
        legacyPreferences.edit { clear() }
        return document
    }

    private fun migrateLegacyArchive(
        projectId: String,
        savedText: String,
        previousSavedText: String?,
    ) {
        val legacyDirectory = File(context.filesDir, "document_versions")
        val targetDirectory = File(projectDirectory(projectId), "versions")
        if (legacyDirectory.isDirectory) {
            targetDirectory.parentFile?.mkdirs()
            if (!legacyDirectory.renameTo(targetDirectory)) {
                targetDirectory.mkdirs()
                legacyDirectory.listFiles().orEmpty().forEach { source ->
                    source.copyTo(File(targetDirectory, source.name), overwrite = false)
                }
                legacyDirectory.deleteRecursively()
            }
        }
        val versions = VersionArchive(targetDirectory)
        if (versions.list().isEmpty()) {
            val now = System.currentTimeMillis()
            if (previousSavedText != null) versions.archive(previousSavedText, now - 1)
            if (savedText.isNotEmpty()) versions.archive(savedText, now)
        }
        updateVersionCount(projectId, versions.list())
    }

    private fun updateVersionCount(id: String, versions: List<StoredVersion>): List<StoredVersion> {
        projectPreferences(id).edit { putInt(KEY_VERSION_COUNT, versions.size) }
        return versions
    }

    private fun uniqueFileName(requested: String, excludingProjectId: String? = null): String {
        val normalized = normalizeFileName(requested)
        val used = listProjects()
            .filterNot { it.id == excludingProjectId }
            .map { it.fileName.lowercase() }
            .toSet()
        if (normalized.lowercase() !in used) return normalized
        val base = normalized.removeSuffix(".md")
        var index = 2
        while ("$base $index.md".lowercase() in used) index++
        return "$base $index.md"
    }

    private fun normalizeFileName(name: String): String =
        name.trim().ifEmpty { DEFAULT_FILE_NAME }.let {
            if (it.endsWith(".md", ignoreCase = true)) it else "$it.md"
        }

    private fun projectIds(): Set<String> =
        catalogPreferences.getStringSet(KEY_PROJECT_IDS, emptySet()).orEmpty().toSet()

    private fun addProjectId(id: String) {
        requireValidProjectId(id)
        catalogPreferences.edit {
            putStringSet(KEY_PROJECT_IDS, projectIds() + id)
            putBoolean(KEY_CATALOG_INITIALIZED, true)
        }
    }

    private fun projectPreferences(id: String): SharedPreferences {
        requireValidProjectId(id)
        return context.getSharedPreferences("project_$id", Context.MODE_PRIVATE)
    }

    private fun projectDirectory(id: String): File {
        requireValidProjectId(id)
        return File(projectsRoot, id)
    }

    private fun versionArchive(id: String): VersionArchive =
        VersionArchive(File(projectDirectory(id), "versions"))

    private fun requireValidProjectId(id: String) {
        require(PROJECT_ID_PATTERN.matches(id)) { "Invalid project id" }
    }

    companion object {
        const val DEFAULT_FILE_NAME = "Без названия.md"
        private const val LEGACY_PREFERENCES = "editor_document"
        private const val CATALOG_PREFERENCES = "project_catalog"
        private const val KEY_CATALOG_INITIALIZED = "catalog_initialized"
        private const val KEY_PROJECT_IDS = "project_ids"
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_SAVED_TEXT = "saved_text"
        private const val KEY_CURRENT_TEXT = "current_text"
        private const val KEY_DOCUMENT_URI = "document_uri"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_HAS_UNSAVED_CHANGES = "has_unsaved_changes"
        private const val KEY_HAS_SAVED_BASE = "has_saved_base"
        private const val KEY_VERSION_COUNT = "version_count"
        private const val KEY_PREVIOUS_SAVED_TEXT = "previous_saved_text"
        private val PROJECT_ID_PATTERN = Regex("^[0-9a-f-]{36}$")
    }
}
