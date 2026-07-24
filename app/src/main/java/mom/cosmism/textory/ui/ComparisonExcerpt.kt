package mom.cosmism.textory.ui

internal data class ExcerptHighlight(
    val start: Int,
    val endExclusive: Int,
)

internal data class ComparisonExcerpt(
    val text: String,
    val highlights: List<ExcerptHighlight>,
    val shortened: Boolean,
)

internal fun comparisonExcerpt(
    text: String,
    counterpart: String,
    maxChars: Int,
): ComparisonExcerpt {
    require(maxChars >= 32)
    if (text.isEmpty()) return ComparisonExcerpt("", emptyList(), shortened = false)

    val (changeStart, changeEnd) = changedRange(text, counterpart)
    if (text.length <= maxChars) {
        return ComparisonExcerpt(
            text = text,
            highlights = highlightFor(changeStart, changeEnd),
            shortened = false,
        )
    }

    val changedLength = changeEnd - changeStart
    return if (changedLength <= maxChars / 2) {
        focusedWindow(text, changeStart, changeEnd, maxChars)
    } else {
        splitChangedWindow(text, changeStart, changeEnd, maxChars)
    }
}

private fun changedRange(text: String, counterpart: String): Pair<Int, Int> {
    if (counterpart.isEmpty()) return 0 to text.length
    var prefix = 0
    val maxPrefix = minOf(text.length, counterpart.length)
    while (prefix < maxPrefix && text[prefix] == counterpart[prefix]) prefix++

    var suffix = 0
    val maxSuffix = minOf(text.length - prefix, counterpart.length - prefix)
    while (
        suffix < maxSuffix &&
        text[text.lastIndex - suffix] == counterpart[counterpart.lastIndex - suffix]
    ) suffix++
    return safeStart(text, prefix) to safeEnd(text, text.length - suffix)
}

private fun focusedWindow(
    text: String,
    changeStart: Int,
    changeEnd: Int,
    maxChars: Int,
): ComparisonExcerpt {
    val contentBudget = maxChars - 4
    val contextBudget = (contentBudget - (changeEnd - changeStart)).coerceAtLeast(0)
    var start = (changeStart - contextBudget / 2).coerceAtLeast(0)
    var end = (start + contentBudget).coerceAtMost(text.length)
    start = safeStart(text, (end - contentBudget).coerceAtLeast(0))
    end = safeEnd(text, end)

    val leading = if (start > 0) "… " else ""
    val trailing = if (end < text.length) " …" else ""
    val display = leading + text.substring(start, end) + trailing
    val intersectionStart = maxOf(changeStart, start)
    val intersectionEnd = minOf(changeEnd, end)
    val highlights = if (intersectionStart < intersectionEnd) {
        listOf(
            ExcerptHighlight(
                start = leading.length + intersectionStart - start,
                endExclusive = leading.length + intersectionEnd - start,
            ),
        )
    } else {
        emptyList()
    }
    return ComparisonExcerpt(display, highlights, shortened = true)
}

private fun splitChangedWindow(
    text: String,
    changeStart: Int,
    changeEnd: Int,
    maxChars: Int,
): ComparisonExcerpt {
    val separator = " … "
    val edgeMarkersBudget = 4
    val contentBudget = maxChars - separator.length - edgeMarkersBudget
    val firstLength = contentBudget / 2
    val secondLength = contentBudget - firstLength
    val firstStart = safeStart(text, (changeStart - 8).coerceAtLeast(0))
    val firstEnd = safeEnd(text, (firstStart + firstLength).coerceAtMost(text.length))
    val secondEnd = safeEnd(text, (changeEnd + 8).coerceAtMost(text.length))
    val secondStart = safeStart(text, (secondEnd - secondLength).coerceAtLeast(firstEnd))

    if (secondStart <= firstEnd) {
        return focusedWindow(text, changeStart, changeEnd, maxChars)
    }

    val leading = if (firstStart > 0) "… " else ""
    val trailing = if (secondEnd < text.length) " …" else ""
    val firstText = text.substring(firstStart, firstEnd)
    val secondText = text.substring(secondStart, secondEnd)
    val secondDisplayStart = leading.length + firstText.length + separator.length
    val highlights = buildList {
        val firstHighlightStart = maxOf(changeStart, firstStart)
        val firstHighlightEnd = minOf(changeEnd, firstEnd)
        if (firstHighlightStart < firstHighlightEnd) {
            add(
                ExcerptHighlight(
                    start = leading.length + firstHighlightStart - firstStart,
                    endExclusive = leading.length + firstHighlightEnd - firstStart,
                ),
            )
        }
        val secondHighlightStart = maxOf(changeStart, secondStart)
        val secondHighlightEnd = minOf(changeEnd, secondEnd)
        if (secondHighlightStart < secondHighlightEnd) {
            add(
                ExcerptHighlight(
                    start = secondDisplayStart + secondHighlightStart - secondStart,
                    endExclusive = secondDisplayStart + secondHighlightEnd - secondStart,
                ),
            )
        }
    }
    return ComparisonExcerpt(
        text = leading + firstText + separator + secondText + trailing,
        highlights = highlights,
        shortened = true,
    )
}

private fun highlightFor(start: Int, endExclusive: Int): List<ExcerptHighlight> =
    if (start < endExclusive) listOf(ExcerptHighlight(start, endExclusive)) else emptyList()

private fun safeStart(text: String, index: Int): Int = when {
    index <= 0 || index >= text.length -> index.coerceIn(0, text.length)
    Character.isLowSurrogate(text[index]) && Character.isHighSurrogate(text[index - 1]) -> index - 1
    else -> index
}

private fun safeEnd(text: String, index: Int): Int = when {
    index <= 0 || index >= text.length -> index.coerceIn(0, text.length)
    Character.isHighSurrogate(text[index - 1]) && Character.isLowSurrogate(text[index]) -> index + 1
    else -> index
}
