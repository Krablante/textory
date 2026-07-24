package mom.cosmism.textory.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionHistoryComparisonTest {
    @Test
    fun selectedSnapshotIsComparedAgainstCurrentUnsavedDraft() {
        val currentDraft = "# Идея\nTextory пишет быстро.\nНовая несохранённая строка."
        val selectedSnapshot = "# Идея\nTextory пишет спокойно."

        val snapshot = calculateHistoricalComparison(currentDraft, selectedSnapshot)

        assertEquals(selectedSnapshot, snapshot.sourceText)
        assertTrue(snapshot.changes.isNotEmpty())
        assertTrue(snapshot.changes.any { "быстро" in it.previousText || "Новая" in it.previousText })
        assertTrue(snapshot.changes.any { "спокойно" in it.currentText })
        assertTrue(snapshot.changes.all { it.currentStart in 0..selectedSnapshot.length })
    }

    @Test
    fun identicalSnapshotHasNoDifferences() {
        val text = "# Идея\nОдинаковый текст"
        assertTrue(calculateHistoricalComparison(text, text).changes.isEmpty())
    }

    @Test
    fun comparisonStatusExplainsModeAndRussianCounts() {
        assertEquals("Текущий черновик", versionComparisonStatus(true, true, false, 0))
        assertEquals("Отличия скрыты", versionComparisonStatus(false, false, false, 5))
        assertEquals("Сравниваем с черновиком…", versionComparisonStatus(false, true, true, 0))
        assertEquals("Совпадает с черновиком", versionComparisonStatus(false, true, false, 0))
        assertEquals("1 отличие с черновиком", versionComparisonStatus(false, true, false, 1))
        assertEquals("2 отличия с черновиком", versionComparisonStatus(false, true, false, 2))
        assertEquals("5 отличий с черновиком", versionComparisonStatus(false, true, false, 5))
        assertEquals("11 отличий с черновиком", versionComparisonStatus(false, true, false, 11))
        assertEquals("21 отличие с черновиком", versionComparisonStatus(false, true, false, 21))
    }
}
