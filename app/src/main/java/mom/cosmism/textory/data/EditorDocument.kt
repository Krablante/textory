package mom.cosmism.textory.data

data class EditorDocument(
    val id: String,
    val fileName: String,
    val savedText: String,
    val currentText: String,
    val hasSavedBase: Boolean,
    val documentUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ProjectSummary(
    val id: String,
    val fileName: String,
    val hasUnsavedChanges: Boolean,
    val savedVersionCount: Int,
    val updatedAt: Long,
)

data class ProjectCatalogSnapshot(
    val projects: List<ProjectSummary>,
    val activeDocument: EditorDocument?,
)

object DemoDocument {
    const val FILE_NAME = "Проект.md"

    val saved = """
        # Идея продукта

        Textory помогает писать спокойно и сосредоточенно, оставляя всё лишнее за пределами экрана.

        Короткая панель помогает быстро записать мысль и сразу вернуться к тексту.

        Сложные панели прятали текст за множеством технических деталей.

        Старый раздел подробно объяснял каждую правку и занимал слишком много места. Читателю приходилось покидать редактор, чтобы понять, что именно изменилось.
    """.trimIndent()

    val current = """
        # Идея продукта

        Textory помогает писать ясно и сосредоточенно, оставляя всё лишнее за пределами экрана.

        Короткая панель помогает бережно оформить важную мысль и сразу вернуться к тексту.

        Редактор остаётся тихим и всегда ставит содержание на первое место.

        Каждое изменение видно прямо в чистом тексте. Одно касание открывает спокойное сравнение, а навигация помогает пройти весь документ. После сохранения документ снова выглядит цельным.
    """.trimIndent()
}
