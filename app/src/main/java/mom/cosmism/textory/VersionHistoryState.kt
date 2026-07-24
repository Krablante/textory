package mom.cosmism.textory

import mom.cosmism.textory.data.ProjectSummary

const val CURRENT_VERSION_ID = "current"

data class VersionItem(
    val id: String,
    val savedAt: Long?,
    val isCurrent: Boolean,
)

data class VersionHistoryUiState(
    val items: List<VersionItem> = listOf(
        VersionItem(id = CURRENT_VERSION_ID, savedAt = null, isCurrent = true),
    ),
    val selectedIndex: Int = 0,
    val texts: Map<String, String> = emptyMap(),
    val loadingIds: Set<String> = emptySet(),
) {
    val selectedItem: VersionItem? get() = items.getOrNull(selectedIndex)
    val selectedText: String? get() = selectedItem?.let { texts[it.id] }
}

data class ProjectCatalogUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true,
)
