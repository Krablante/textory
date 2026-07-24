package mom.cosmism.textory.ui

import mom.cosmism.textory.diff.TextChange

internal enum class ComparisonDockLayout {
    ADDITION,
    DELETION,
    REPLACEMENT,
}

internal enum class ReplacementPresentation {
    SIDE_BY_SIDE,
    STACKED,
}

internal fun comparisonDockLayout(change: TextChange): ComparisonDockLayout = when {
    change.previousText.isEmpty() && change.currentText.isNotEmpty() -> ComparisonDockLayout.ADDITION
    change.currentText.isEmpty() && change.previousText.isNotEmpty() -> ComparisonDockLayout.DELETION
    else -> ComparisonDockLayout.REPLACEMENT
}

internal fun replacementPresentation(change: TextChange): ReplacementPresentation {
    val longest = maxOf(change.currentText.length, change.previousText.length)
    val lineCount = maxOf(change.currentText.lineSequence().count(), change.previousText.lineSequence().count())
    return if (longest <= 48 && lineCount <= 2) {
        ReplacementPresentation.SIDE_BY_SIDE
    } else {
        ReplacementPresentation.STACKED
    }
}
