package mom.cosmism.textory.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonExcerptTest {
    @Test
    fun shortDifferenceRemainsComplete() {
        val excerpt = comparisonExcerpt("Редактор тихий", "Редактор сложный", maxChars = 80)

        assertEquals("Редактор тихий", excerpt.text)
        assertFalse(excerpt.shortened)
        assertTrue(excerpt.highlights.isNotEmpty())
        assertEquals("тихи", highlightedText(excerpt))
    }

    @Test
    fun smallChangeKeepsNearbyContextAndDropsRemoteContext() {
        val commonPrefix = "Начало документа. ".repeat(12)
        val commonSuffix = " Конец документа.".repeat(12)
        val excerpt = comparisonExcerpt(
            text = commonPrefix + "НОВАЯ МЫСЛЬ" + commonSuffix,
            counterpart = commonPrefix + "СТАРАЯ МЫСЛЬ" + commonSuffix,
            maxChars = 96,
        )

        assertTrue(excerpt.shortened)
        assertTrue(excerpt.text.startsWith("… "))
        assertTrue(excerpt.text.endsWith(" …"))
        assertTrue("НОВАЯ МЫСЛЬ" in excerpt.text)
        assertEquals("НОВ", highlightedText(excerpt))
    }

    @Test
    fun hugeChangedBlockShowsBothEdgesWithoutScrolling() {
        val text = "Первое важное предложение. " + "середина ".repeat(80) + "Последнее важное предложение."
        val excerpt = comparisonExcerpt(text, counterpart = "", maxChars = 120)

        assertTrue(excerpt.shortened)
        assertTrue("Первое важное" in excerpt.text)
        assertTrue("Последнее важное" in excerpt.text)
        assertTrue(" … " in excerpt.text)
        assertTrue(excerpt.text.length <= 124)
        assertEquals(2, excerpt.highlights.size)
    }

    @Test
    fun excerptNeverSplitsEmojiSurrogatePairs() {
        val text = "😀".repeat(80) + " важная мысль " + "🚀".repeat(80)
        val excerpt = comparisonExcerpt(text, counterpart = "", maxChars = 64)

        assertEquals(
            excerpt.text,
            excerpt.text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8),
        )
    }

    private fun highlightedText(excerpt: ComparisonExcerpt): String = excerpt.highlights.joinToString("|") {
        excerpt.text.substring(it.start, it.endExclusive)
    }
}
