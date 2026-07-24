package mom.cosmism.textory.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class MarkdownAction(val label: String, val accessibilityLabel: String) {
    HEADING("H₁", "Заголовок"),
    BOLD("B", "Жирный текст"),
    ITALIC("I", "Курсив"),
    BULLET("•", "Маркированный список"),
    NUMBERED("1.", "Нумерованный список"),
    CHECKBOX("☑", "Чекбокс"),
    CODE("<>", "Код"),
    LINK("🔗", "Ссылка"),
}

object MarkdownFormatter {
    fun apply(value: TextFieldValue, action: MarkdownAction): TextFieldValue = when (action) {
        MarkdownAction.HEADING -> prefixLines(value, "# ")
        MarkdownAction.BOLD -> wrap(value, "**", "**", "жирный текст")
        MarkdownAction.ITALIC -> wrap(value, "*", "*", "курсив")
        MarkdownAction.BULLET -> prefixLines(value, "- ")
        MarkdownAction.NUMBERED -> prefixLines(value, "1. ")
        MarkdownAction.CHECKBOX -> prefixLines(value, "- [ ] ")
        MarkdownAction.CODE -> wrap(value, "`", "`", "код")
        MarkdownAction.LINK -> link(value)
    }

    private fun wrap(
        value: TextFieldValue,
        before: String,
        after: String,
        placeholder: String,
    ): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val selected = value.text.substring(start, end)
        val content = selected.ifEmpty { placeholder }
        val replacement = before + content + after
        val updated = value.text.replaceRange(start, end, replacement)
        val selection = if (selected.isEmpty()) {
            TextRange(start + before.length, start + before.length + content.length)
        } else {
            TextRange(start + replacement.length)
        }
        return TextFieldValue(updated, selection)
    }

    private fun link(value: TextFieldValue): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val selected = value.text.substring(start, end).ifEmpty { "текст" }
        val replacement = "[$selected](https://)"
        val updated = value.text.replaceRange(start, end, replacement)
        val urlStart = start + selected.length + 3
        return TextFieldValue(updated, TextRange(urlStart, urlStart + "https://".length))
    }

    private fun prefixLines(value: TextFieldValue, prefix: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val lineStart = value.text.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }
        val lineEnd = value.text.indexOf('\n', end).let { if (it == -1) value.text.length else it }
        val original = value.text.substring(lineStart, lineEnd)
        val replacement = original.lineSequence().joinToString("\n") { line ->
            if (line.startsWith(prefix)) line.removePrefix(prefix) else prefix + line
        }
        val updated = value.text.replaceRange(lineStart, lineEnd, replacement)
        return TextFieldValue(updated, TextRange(lineStart + replacement.length))
    }
}
