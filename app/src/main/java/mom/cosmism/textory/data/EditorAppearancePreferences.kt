package mom.cosmism.textory.data

import android.content.Context
import androidx.core.content.edit
import kotlin.math.roundToInt

const val DEFAULT_EDITOR_FONT_SIZE_SP = 17f
const val MIN_EDITOR_FONT_SIZE_SP = 1f

class EditorAppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readFontSizeSp(): Float = normalizeEditorFontSizeSp(
        preferences.getFloat(KEY_FONT_SIZE_SP, DEFAULT_EDITOR_FONT_SIZE_SP),
    )

    fun writeFontSizeSp(value: Float) {
        preferences.edit { putFloat(KEY_FONT_SIZE_SP, normalizeEditorFontSizeSp(value)) }
    }

    private companion object {
        const val PREFERENCES_NAME = "editor_appearance"
        const val KEY_FONT_SIZE_SP = "font_size_sp"
    }
}

fun normalizeEditorFontSizeSp(value: Float): Float = when {
    !value.isFinite() -> DEFAULT_EDITOR_FONT_SIZE_SP
    else -> value.roundToInt().toFloat().coerceAtLeast(MIN_EDITOR_FONT_SIZE_SP)
}
