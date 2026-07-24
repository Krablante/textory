package mom.cosmism.textory.ui

import mom.cosmism.textory.diff.ChangeScale
import mom.cosmism.textory.diff.TextChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeHitResolverTest {
    @Test
    fun `boundary offset belongs to following half-open range`() {
        val first = change("first", 0, 5)
        val second = change("second", 5, 10)

        val selected = ChangeHitResolver.atOffset(
            listOf(candidate(first), candidate(second)),
            offset = 5,
        )

        assertEquals(second, selected)
    }

    @Test
    fun `overlap resolves to most specific visible range`() {
        val broad = change("broad", 0, 20)
        val narrow = change("narrow", 5, 10)

        val selected = ChangeHitResolver.mostSpecific(
            listOf(
                ChangeHitCandidate(broad, visibleSpanLength = 20),
                ChangeHitCandidate(narrow, visibleSpanLength = 5),
            ),
        )

        assertEquals(narrow, selected)
    }

    @Test
    fun `two of three double tap samples must agree`() {
        val wrong = change("wrong", 0, 5)
        val target = change("target", 5, 20)

        assertEquals(target, ChangeHitResolver.fromDoubleTapVotes(listOf(wrong, target, target)))
        assertNull(ChangeHitResolver.fromDoubleTapVotes(listOf(wrong, target, null)))
    }

    @Test
    fun `native selection prefers fully covered narrow change over broad overlap`() {
        val broad = change("broad", 0, 30)
        val narrow = change("narrow", 6, 12)

        val selected = ChangeHitResolver.intersectingSelection(
            changes = listOf(broad, narrow),
            selectionStart = 6,
            selectionEnd = 12,
        )

        assertEquals(narrow, selected)
    }

    @Test
    fun `selection outside changes does not invent a hit`() {
        val change = change("change", 10, 20)

        assertNull(ChangeHitResolver.intersectingSelection(listOf(change), 0, 5))
    }

    @Test
    fun `maps adjacent source ranges to non-overlapping display ranges`() {
        val first = change("first", 100, 105)
        val second = change("second", 105, 110)

        val mapped = mapChangesToDisplayRanges(
            rawOffsets = IntArray(10) { 100 + it },
            changes = listOf(first, second),
        )

        assertEquals(
            listOf(
                BlockHighlight(first, DisplayHighlight(0, 5)),
                BlockHighlight(second, DisplayHighlight(5, 10)),
            ),
            mapped,
        )
    }

    @Test
    fun `hidden Markdown offsets do not bridge unrelated display runs`() {
        val change = change("sparse", 100, 103)

        val mapped = mapChangesToDisplayRanges(
            rawOffsets = intArrayOf(100, 101, 110, 111, 102),
            changes = listOf(change),
        )

        assertEquals(
            listOf(
                BlockHighlight(change, DisplayHighlight(0, 2)),
                BlockHighlight(change, DisplayHighlight(4, 5)),
            ),
            mapped,
        )
    }

    private fun candidate(change: TextChange) = ChangeHitCandidate(
        change = change,
        visibleSpanLength = change.currentEnd - change.currentStart,
    )

    private fun change(id: String, start: Int, end: Int) = TextChange(
        id = id,
        currentStart = start,
        currentEnd = end,
        previousText = "old-$id",
        currentText = "new-$id",
        scale = ChangeScale.WORD,
        anchorOffset = start,
    )
}
