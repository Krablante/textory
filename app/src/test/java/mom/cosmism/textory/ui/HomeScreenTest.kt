package mom.cosmism.textory.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenTest {
    @Test
    fun documentCountUsesRussianPluralForms() {
        assertEquals("0 документов", documentCountLabel(0))
        assertEquals("1 документ", documentCountLabel(1))
        assertEquals("2 документа", documentCountLabel(2))
        assertEquals("5 документов", documentCountLabel(5))
        assertEquals("11 документов", documentCountLabel(11))
        assertEquals("21 документ", documentCountLabel(21))
        assertEquals("24 документа", documentCountLabel(24))
    }
}
