package mom.cosmism.textory.data

import android.content.Context
import androidx.core.content.edit

enum class AppTheme(
    val storageValue: String,
    val isDark: Boolean,
) {
    LIGHT("light", isDark = false),
    SEPIA("sepia", isDark = false),
    DARK("dark", isDark = true),
    ;

    companion object {
        fun fromStorageValue(value: String?): AppTheme =
            entries.firstOrNull { it.storageValue == value } ?: LIGHT
    }
}

class AppThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppTheme = AppTheme.fromStorageValue(preferences.getString(THEME_KEY, null))

    fun save(theme: AppTheme) {
        preferences.edit { putString(THEME_KEY, theme.storageValue) }
    }

    private companion object {
        const val PREFERENCES_NAME = "textory_appearance"
        const val THEME_KEY = "app_theme"
    }
}
