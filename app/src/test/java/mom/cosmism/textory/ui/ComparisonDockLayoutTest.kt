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

    @Test
    fun `short replacement stays side by side`() {
        assertEquals(
            ReplacementPresentation.SIDE_BY_SIDE,
            replacementPresentation(change(previous = "Было", current = "Сейчас")),
        )
    }

    @Test
    fun `long replacement stacks full-width blocks`() {
        assertEquals(
            ReplacementPresentation.STACKED,
            replacementPresentation(
                change(
                    previous = "Старый подробный абзац. ".repeat(8),
                    current = "Новый подробный абзац. ".repeat(8),
                ),
            ),
        )
    }

    @Test
    fun `sentence-sized replacement avoids narrow columns`() {
        assertEquals(
            ReplacementPresentation.STACKED,
            replacementPresentation(
                change(
                    previous = "Сложные панели прятали текст за множеством технических деталей.",
                    current = "Редактор остаётся тихим и всегда ставит содержание на первое место.",
                ),
            ),
        )
    }

    @Test
    fun `dock font stays compact and readable`() {
        assertEquals(15f, comparisonDockFontSize(preferredSp = 24f, sourceLength = 80, maxChars = 140))
        assertEquals(14f, comparisonDockFontSize(preferredSp = 15f, sourceLength = 180, maxChars = 140))
        assertEquals(13f, comparisonDockFontSize(preferredSp = 15f, sourceLength = 400, maxChars = 140))
        assertEquals(11f, comparisonDockFontSize(preferredSp = 9f, sourceLength = 400, maxChars = 140))
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
