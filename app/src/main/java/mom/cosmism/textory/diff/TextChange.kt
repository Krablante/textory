package mom.cosmism.textory.diff

enum class ChangeScale {
    WORD,
    PHRASE,
    SENTENCE,
    PARAGRAPH,
    ADDED,
    DELETED,
}

data class TextChange(
    val id: String,
    val currentStart: Int,
    val currentEnd: Int,
    val previousText: String,
    val currentText: String,
    val scale: ChangeScale,
    val anchorOffset: Int = currentStart,
) {
    val isDeletion: Boolean get() = currentStart == currentEnd && previousText.isNotEmpty()
}

data class DiffSnapshot(
    val sourceText: String,
    val changes: List<TextChange>,
)
