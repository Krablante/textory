package mom.cosmism.textory.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class AdaptiveDiffEngineTest {
    @Test
    fun `aligns the complete built-in demo as four meaningful replacements`() {
        val previous = """
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
        val changes = compare(previous, current)

        assertFragments(
            changes,
            "спокойно" to "ясно",
            "быстро записать" to "бережно оформить важную",
            "Сложные панели прятали текст за множеством технических деталей." to
                "Редактор остаётся тихим и всегда ставит содержание на первое место.",
            "Старый раздел подробно объяснял каждую правку и занимал слишком много места. Читателю приходилось покидать редактор, чтобы понять, что именно изменилось." to
                "Каждое изменение видно прямо в чистом тексте. Одно касание открывает спокойное сравнение, а навигация помогает пройти весь документ. После сохранения документ снова выглядит цельным.",
        )
    }

    @Test
    fun `isolates one Russian word replacement`() {
        val changes = compare("Писать нужно спокойно.", "Писать нужно ясно.")

        assertFragments(changes, "спокойно" to "ясно")
        assertEquals(ChangeScale.WORD, changes.single().scale)
    }

    @Test
    fun `keeps stable paragraphs aligned after insertion at start`() {
        val current = "Новый абзац.\n\nПервый стабильный.\n\nВторой стабильный."
        val changes = compare("Первый стабильный.\n\nВторой стабильный.", current)

        assertFragments(changes, "" to "Новый абзац.")
        assertValidRanges(current, changes)
    }

    @Test
    fun `pairs a rewritten paragraph beside a new insertion`() {
        val previous = "Первый старый абзац.\n\nФинал стабилен."
        val current = "Совершенно новая вставка.\n\nПервый обновлённый абзац.\n\nФинал стабилен."
        val changes = compare(previous, current)

        assertFragments(
            changes,
            "" to "Совершенно новая вставка.",
            "старый" to "обновлённый",
        )
    }

    @Test
    fun `pairs a rewritten paragraph beside a deletion`() {
        val previous = "Лишний отдельный блок.\n\nПервый старый абзац.\n\nФинал стабилен."
        val current = "Первый обновлённый абзац.\n\nФинал стабилен."
        val changes = compare(previous, current)

        assertEquals(2, changes.size)
        assertTrue(changes.first().isDeletion)
        assertEquals("Лишний отдельный блок.", changes.first().previousText)
        assertEquals("старый", changes.last().previousText)
        assertEquals("обновлённый", changes.last().currentText)
    }

    @Test
    fun `isolates inserted Markdown list item`() {
        val current = "- первый\n- новый пункт\n- второй\n- третий"
        val changes = compare("- первый\n- второй\n- третий", current)

        assertFragments(changes, "" to "- новый пункт")
    }

    @Test
    fun `numbered insertion does not detach the following rewritten item`() {
        val previous = buildString {
            (1..20).forEach { appendLine("$it. Stable line") }
            appendLine("21. PR old contract.")
            append("22. Tail")
        }
        val current = buildString {
            (1..20).forEach { appendLine("$it. Stable line") }
            appendLine("21. Investigation and planning")
            appendLine("22. PR current target contract.")
            append("23. Tail")
        }
        val changes = compare(previous, current)

        assertTrue(changes.any { it.previousText.isEmpty() && it.currentText == "21. Investigation and planning" })
        assertTrue(changes.any { it.previousText == "old" && it.currentText == "current target" })
        assertTrue(changes.none { it.previousText.isEmpty() && "PR current" in it.currentText })
        assertTrue(changes.none { "Tail" in it.previousText || "Tail" in it.currentText })
    }

    @Test
    fun `chooses the changed occurrence among repeated words`() {
        val changes = compare("да да да нет", "да да точно нет")

        assertFragments(changes, "да" to "точно")
    }

    @Test
    fun `does not swallow an unchanged conjunction between edits`() {
        val changes = compare("быстро и небрежно", "медленно и аккуратно")

        assertFragments(
            changes,
            "быстро" to "медленно",
            "небрежно" to "аккуратно",
        )
    }

    @Test
    fun `isolates punctuation replacement`() {
        val changes = compare("Это важно.", "Это важно!")

        assertFragments(changes, "." to "!")
    }

    @Test
    fun `keeps Markdown code delimiters outside a suffix insertion`() {
        val changes = compare(
            "Используйте `buro-data-maintenance`.",
            "Используйте `buro-data-maintenance-v2`.",
        )

        assertFragments(changes, "" to "-v2")
    }

    @Test
    fun `isolates changed URL path segment`() {
        val changes = compare(
            "[Документ](https://example.test/old/page)",
            "[Документ](https://example.test/new/page)",
        )

        assertFragments(changes, "old" to "new")
    }

    @Test
    fun `uses whole lexical word for an internal typo`() {
        val changes = compare("Нужна синхронизация данных.", "Нужна синхроницация данных.")

        assertFragments(changes, "синхронизация" to "синхроницация")
    }

    @Test
    fun `treats one completely rewritten paragraph as replacement`() {
        val previous = "Старое предложение полностью посвящено архитектуре."
        val current = "Новый текст теперь описывает пользовательский сценарий."
        val changes = compare(previous, current)

        assertEquals(1, changes.size)
        assertEquals(previous, changes.single().previousText)
        assertEquals(current, changes.single().currentText)
        assertFalse(changes.single().isDeletion)
    }

    @Test
    fun `represents removed middle paragraph with one anchored deletion`() {
        val previous = "Первый.\n\nУдаляемый абзац.\n\nПоследний."
        val current = "Первый.\n\nПоследний."
        val changes = compare(previous, current)

        assertEquals(1, changes.size)
        assertTrue(changes.single().isDeletion)
        assertEquals("Удаляемый абзац.", changes.single().previousText)
        assertEquals(current.indexOf("Последний."), changes.single().anchorOffset)
    }

    @Test
    fun `preserves original separators when multiple list items are deleted`() {
        val previous = "- первый\n- удалить один\n- удалить два\n- последний"
        val current = "- первый\n- последний"
        val changes = compare(previous, current)

        assertEquals(1, changes.size)
        assertTrue(changes.single().isDeletion)
        assertEquals("- удалить один\n- удалить два", changes.single().previousText)
    }

    @Test
    fun `isolates edit inside fenced Markdown code`() {
        val changes = compare(
            "```kotlin\nval answer = 41\n```",
            "```kotlin\nval answer = 42\n```",
        )

        assertFragments(changes, "41" to "42")
    }

    @Test
    fun `keeps heading marker outside a title edit`() {
        val changes = compare("## Старый заголовок", "## Новый заголовок")

        assertFragments(changes, "Старый" to "Новый")
    }

    @Test
    fun `detects Markdown hard break whitespace`() {
        val changes = compare("Строка\n", "Строка  \n")

        assertFragments(changes, "" to "  ")
    }

    @Test
    fun `keeps ZWJ emoji as one fragment`() {
        val changes = compare("Автор 👩‍💻 пишет", "Автор 👨‍💻 пишет")

        assertFragments(changes, "👩‍💻" to "👨‍💻")
    }

    @Test
    fun `inserts one block among repeated identical blocks`() {
        val previous = "Повтор.\n\nПовтор.\n\nФинал."
        val current = "Повтор.\n\nНовая вставка.\n\nПовтор.\n\nФинал."
        val changes = compare(previous, current)

        assertFragments(changes, "" to "Новая вставка.")
    }

    @Test
    fun `does not mark stable block when another block moves`() {
        val previous = "Альфа уникальная.\n\nБета уникальная.\n\nГамма стабильная."
        val current = "Бета уникальная.\n\nАльфа уникальная.\n\nГамма стабильная."
        val changes = compare(previous, current)

        assertTrue(changes.isNotEmpty())
        assertTrue(changes.none { "Гамма" in it.currentText || "Гамма" in it.previousText })
        assertTrue(changes.size <= 2)
    }

    @Test
    fun `trims unchanged outer whitespace from comparison fragments`() {
        val changes = compare("До старое после", "До новое после")

        assertFragments(changes, "старое" to "новое")
        assertTrue(changes.none { it.currentText.startsWith(' ') || it.currentText.endsWith(' ') })
    }

    @Test
    fun `keeps emoji sequence as one visible fragment`() {
        val changes = compare("Статус ✅ готов", "Статус ⚠️ готов")

        assertFragments(changes, "✅" to "⚠️")
    }

    @Test
    fun `preserves a large stable document around one edit`() {
        val previous = (1..1_000).joinToString("\n\n") { "Абзац $it остаётся стабильным." }
        val current = previous.replace("Абзац 777 остаётся стабильным.", "Абзац 777 теперь изменён.")
        lateinit var changes: List<TextChange>

        val elapsed = measureTimeMillis { changes = compare(previous, current) }

        assertFragments(changes, "остаётся стабильным" to "теперь изменён")
        assertTrue("diff took ${elapsed}ms", elapsed < 3_000)
    }

    @Test
    fun `bounds work for a large completely replaced block`() {
        val previous = List(8_000) { "старое" }.joinToString(" ")
        val current = List(8_000) { "новое" }.joinToString(" ")
        lateinit var changes: List<TextChange>

        val elapsed = measureTimeMillis { changes = compare(previous, current) }

        assertEquals(1, changes.size)
        assertEquals(previous, changes.single().previousText)
        assertEquals(current, changes.single().currentText)
        assertTrue("diff took ${elapsed}ms", elapsed < 3_000)
    }

    @Test
    fun `isolates one word in a huge single paragraph without excessive work`() {
        val prefix = "стабильное слово ".repeat(20_000)
        val suffix = " продолжение".repeat(20_000)
        val previous = prefix + "ошибочное" + suffix
        val current = prefix + "исправленное" + suffix
        lateinit var changes: List<TextChange>

        val elapsed = measureTimeMillis { changes = compare(previous, current) }

        assertFragments(changes, "ошибочное" to "исправленное")
        assertTrue("diff took ${elapsed}ms", elapsed < 3_000)
    }

    @Test
    fun `bounds work for thousands of completely rewritten paragraphs`() {
        val previous = (1..2_500).joinToString("\n\n") { "Старый независимый абзац $it." }
        val current = (1..2_500).joinToString("\n\n") { "Новый отдельный фрагмент $it." }
        lateinit var changes: List<TextChange>

        val elapsed = measureTimeMillis { changes = compare(previous, current) }

        assertEquals(2_500, changes.size)
        assertValidRanges(current, changes)
        assertTrue("diff took ${elapsed}ms", elapsed < 3_000)
    }

    @Test
    fun `survives deterministic fuzz edits with valid ranges`() {
        val random = Random(7)
        val vocabulary = listOf("альфа", "бета", "гамма", "delta", "`code`", "✅", "!", "- пункт")
        var previous = (1..80).joinToString(" ") { vocabulary[it % vocabulary.size] }

        repeat(150) {
            val words = previous.split(' ').toMutableList()
            when (random.nextInt(3)) {
                0 -> words.add(random.nextInt(words.size + 1), vocabulary.random(random))
                1 -> if (words.isNotEmpty()) words.removeAt(random.nextInt(words.size))
                else -> if (words.isNotEmpty()) words[random.nextInt(words.size)] = vocabulary.random(random)
            }
            val current = words.joinToString(" ")
            val changes = compare(previous, current)
            assertValidRanges(current, changes)
            previous = current
        }
    }

    @Test
    fun `is deterministic and always returns valid ordered ranges`() {
        val previous = "раз два раз два раз три"
        val current = "раз два новый два раз четыре"
        val first = compare(previous, current)

        repeat(20) {
            assertEquals(first, compare(previous, current))
        }
        assertValidRanges(current, first)
    }

    private fun compare(previous: String, current: String): List<TextChange> =
        AdaptiveDiffEngine.calculate(previous, current).changes

    private fun assertFragments(changes: List<TextChange>, vararg expected: Pair<String, String>) {
        assertEquals(
            expected.toList(),
            changes.map { it.previousText to it.currentText },
        )
    }

    private fun assertValidRanges(current: String, changes: List<TextChange>) {
        var previousEnd = 0
        changes.forEach { change ->
            assertTrue(change.currentStart in 0..current.length)
            assertTrue(change.currentEnd in change.currentStart..current.length)
            assertEquals(change.currentText, current.substring(change.currentStart, change.currentEnd))
            assertTrue(change.currentStart >= previousEnd || change.isDeletion)
            if (!change.isDeletion) previousEnd = change.currentEnd
        }
    }
}
