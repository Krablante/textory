package mom.cosmism.textory.ui

import mom.cosmism.textory.diff.TextChange

internal data class ChangeHitCandidate(
    val change: TextChange,
    val visibleSpanLength: Int,
)

internal data class BlockHighlight(
    val change: TextChange,
    val display: DisplayHighlight,
)

internal fun mapChangesToDisplayRanges(
    rawOffsets: IntArray,
    changes: List<TextChange>,
): List<BlockHighlight> = changes
    .asSequence()
    .filterNot(TextChange::isDeletion)
    .flatMap { change ->
        buildList {
            var runStart = -1
            for (index in 0..rawOffsets.size) {
                val inside = index < rawOffsets.size &&
                    rawOffsets[index] >= change.currentStart &&
                    rawOffsets[index] < change.currentEnd
                if (inside && runStart < 0) runStart = index
                if (!inside && runStart >= 0) {
                    add(
                        BlockHighlight(
                            change = change,
                            display = DisplayHighlight(runStart, index),
                        ),
                    )
                    runStart = -1
                }
            }
        }.asSequence()
    }
    .distinctBy { listOf(it.change.id, it.display.start, it.display.end) }
    .sortedWith(
        compareBy<BlockHighlight> { it.display.start }
            .thenBy { it.display.end }
            .thenBy { it.change.id },
    )
    .toList()

internal object ChangeHitResolver {
    fun atOffset(candidates: List<ChangeHitCandidate>, offset: Int): TextChange? =
        mostSpecific(candidates.filter { candidate ->
            offset >= candidate.change.currentStart && offset < candidate.change.currentEnd
        })

    fun mostSpecific(candidates: List<ChangeHitCandidate>): TextChange? = candidates
        .distinctBy { it.change.id }
        .minWithOrNull(
            compareBy<ChangeHitCandidate> { it.visibleSpanLength }
                .thenBy { it.change.currentEnd - it.change.currentStart }
                .thenBy { it.change.currentText.length }
                .thenBy { it.change.id },
        )
        ?.change

    fun fromDoubleTapVotes(votes: List<TextChange?>): TextChange? {
        val winner = votes.filterNotNull()
            .groupBy(TextChange::id)
            .values
            .map { matching -> matching.first() to matching.size }
            .sortedWith(
                compareByDescending<Pair<TextChange, Int>> { it.second }
                    .thenBy { it.first.currentEnd - it.first.currentStart }
                    .thenBy { it.first.currentText.length }
                    .thenBy { it.first.id },
            )
            .firstOrNull()
        return winner?.first?.takeIf { winner.second >= 2 }
    }

    fun intersectingSelection(
        changes: List<TextChange>,
        selectionStart: Int,
        selectionEnd: Int,
    ): TextChange? {
        if (selectionStart >= selectionEnd) return null
        return changes.asSequence()
            .filterNot(TextChange::isDeletion)
            .mapNotNull { change ->
                val overlap = minOf(selectionEnd, change.currentEnd) - maxOf(selectionStart, change.currentStart)
                if (overlap <= 0) null else SelectionCandidate(change, overlap)
            }
            .sortedWith(
                compareByDescending<SelectionCandidate> {
                    it.overlap.toDouble() / (it.change.currentEnd - it.change.currentStart).coerceAtLeast(1)
                }
                    .thenByDescending(SelectionCandidate::overlap)
                    .thenBy { it.change.currentEnd - it.change.currentStart }
                    .thenBy { it.change.id },
            )
            .firstOrNull()
            ?.change
    }

    private data class SelectionCandidate(
        val change: TextChange,
        val overlap: Int,
    )
}
