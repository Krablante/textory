package mom.cosmism.textory.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDialogTest {
    @Test
    fun byteSizesUseCompactReadableUnits() {
        assertEquals("900 Б", formatBytes(900))
        assertEquals("2 КБ", formatBytes(2048))
        assertEquals("1.5 МБ", formatBytes(1_572_864))
    }
}
