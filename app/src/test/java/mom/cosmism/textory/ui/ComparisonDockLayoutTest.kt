package mom.cosmism.textory.ui

import mom.cosmism.textory.diff.ChangeScale
import mom.cosmism.textory.diff.TextChange
import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonDockLayoutTest {
    @Test
    fun `addition uses full-width current layout`() {
        assertEquals(
            ComparisonDockLayout.ADDITION,
            comparisonDockLayout(change(previous = "", current = "Новый фрагмент")),
        )
    }

    @Test
    fun `deletion uses full-width previous layout`() {
        assertEquals(
            ComparisonDockLayout.DELETION,
            comparisonDockLayout(change(previous = "Удалённый фрагмент", current = "")),
        )
    }

    @Test
    fun `replacement keeps side-by-side layout`() {
        assertEquals(
            ComparisonDockLayout.REPLACEMENT,
            comparisonDockLayout(change(previous = "Было", current = "Сейчас")),
        )
    }

    private fun change(previous: String, current: String) = TextChange(
        id = "$previous->$current",
        currentStart = 0,
        currentEnd = current.length,
        previousText = previous,
        currentText = current,
        scale = ChangeScale.WORD,
        anchorOffset = 0,
    )
}
