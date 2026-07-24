package mom.cosmism.textory.data

import mom.cosmism.textory.ui.editorHeadingFontSizeSp
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorAppearancePreferencesTest {
    @Test
    fun `keeps practical sizes above the former upper limit`() {
        assertEquals(25f, normalizeEditorFontSizeSp(25f))
        assertEquals(72f, normalizeEditorFontSizeSp(72f))
        assertEquals(200f, normalizeEditorFontSizeSp(200f))
    }

    @Test
    fun `rounds font size to whole sp`() {
        assertEquals(18f, normalizeEditorFontSizeSp(17.6f))
    }

    @Test
    fun `uses only the technical positive minimum`() {
        assertEquals(1f, normalizeEditorFontSizeSp(1f))
        assertEquals(1f, normalizeEditorFontSizeSp(0f))
        assertEquals(1f, normalizeEditorFontSizeSp(-100f))
    }

    @Test
    fun `recovers from a non finite stored value`() {
        assertEquals(DEFAULT_EDITOR_FONT_SIZE_SP, normalizeEditorFontSizeSp(Float.NaN))
        assertEquals(DEFAULT_EDITOR_FONT_SIZE_SP, normalizeEditorFontSizeSp(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `markdown headings scale with unrestricted editor size`() {
        assertEquals(35.176f, editorHeadingFontSizeSp(26f, 1), 0.001f)
        assertEquals(7.412f, editorHeadingFontSizeSp(6f, 2), 0.001f)
    }
}
