package mom.cosmism.textory.ui

import mom.cosmism.textory.diff.TextChange

internal enum class ComparisonDockLayout {
    ADDITION,
    DELETION,
    REPLACEMENT,
}

internal fun comparisonDockLayout(change: TextChange): ComparisonDockLayout = when {
    change.previousText.isEmpty() && change.currentText.isNotEmpty() -> ComparisonDockLayout.ADDITION
    change.currentText.isEmpty() && change.previousText.isNotEmpty() -> ComparisonDockLayout.DELETION
    else -> ComparisonDockLayout.REPLACEMENT
}
