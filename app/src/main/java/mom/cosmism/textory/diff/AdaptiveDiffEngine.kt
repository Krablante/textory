package mom.cosmism.textory.diff

import com.github.difflib.DiffUtils
import com.github.difflib.algorithm.myers.MyersDiffWithLinearSpace
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A deterministic human-text diff:
 *
 * 1. split Markdown into structural blocks;
 * 2. align unique blocks with patience anchors and exact gaps with Myers;
 * 3. pair rewritten blocks by token similarity;
 * 4. run Myers on Unicode-aware lexical tokens;
 * 5. clean ranges to visible semantic boundaries.
 */
object AdaptiveDiffEngine {
    private const val MAX_BLOCK_MYERS_UNITS = 4_000
    private const val MAX_TOKEN_MYERS_UNITS = 8_000
    private const val MAX_TOKENIZED_CHARACTERS = 500_000
    private const val MAX_PAIRING_CELLS = 40_000
    private const val MIN_BLOCK_PAIR_SIMILARITY = 0.18

    private val whitespace = Regex("\\s+")
    private val structuralLine = Regex(
        """^(?:#{1,6}\s|[-+*]\s|\d+[.)]\s|>\s?|```|~~~|\|.*\||---+\s*$|___+\s*$|\*\*\*+\s*$)""",
    )

    fun calculate(previous: String, current: String): DiffSnapshot {
        if (previous == current) return DiffSnapshot(current, emptyList())

        val oldBlocks = splitMarkdownBlocks(previous)
        val newBlocks = splitMarkdownBlocks(current)
        if (oldBlocks.isEmpty() && newBlocks.isEmpty()) {
            return DiffSnapshot(current, whitespaceOnlyChange(previous, current))
        }

        val matches = alignStableBlocks(oldBlocks, newBlocks)
        val changes = mutableListOf<TextChange>()
        var oldCursor = 0
        var newCursor = 0

        (matches + BlockMatch(oldBlocks.size, newBlocks.size)).forEach { match ->
            compareBlockRegion(
                oldBlocks = oldBlocks,
                newBlocks = newBlocks,
                previousSource = previous,
                oldStart = oldCursor,
                oldEnd = match.oldIndex,
                newStart = newCursor,
                newEnd = match.newIndex,
                regionEndAnchor = newBlocks.getOrNull(match.newIndex)?.start ?: current.length,
                currentLength = current.length,
                changes = changes,
            )
            if (match.oldIndex < oldBlocks.size && match.newIndex < newBlocks.size) {
                comparePairedBlocks(oldBlocks[match.oldIndex], newBlocks[match.newIndex], changes)
                oldCursor = match.oldIndex + 1
                newCursor = match.newIndex + 1
            }
        }

        return DiffSnapshot(current, normalizeChanges(changes, current.length))
    }

    private fun compareBlockRegion(
        oldBlocks: List<TextBlock>,
        newBlocks: List<TextBlock>,
        previousSource: String,
        oldStart: Int,
        oldEnd: Int,
        newStart: Int,
        newEnd: Int,
        regionEndAnchor: Int,
        currentLength: Int,
        changes: MutableList<TextChange>,
    ) {
        val oldRegion = oldBlocks.subList(oldStart, oldEnd)
        val newRegion = newBlocks.subList(newStart, newEnd)
        val pairs = pairRewrittenBlocks(oldRegion, newRegion)
        var oldCursor = 0
        var newCursor = 0

        (pairs + BlockMatch(oldRegion.size, newRegion.size)).forEach { pair ->
            val pairAnchor = newRegion.getOrNull(pair.newIndex)?.start ?: regionEndAnchor
            addDeletedBlocks(
                blocks = oldRegion.subList(oldCursor, pair.oldIndex),
                previousSource = previousSource,
                anchor = pairAnchor,
                currentLength = currentLength,
                changes = changes,
            )
            newRegion.subList(newCursor, pair.newIndex).forEach { addWholeBlock(it, changes) }

            if (pair.oldIndex < oldRegion.size && pair.newIndex < newRegion.size) {
                comparePairedBlocks(oldRegion[pair.oldIndex], newRegion[pair.newIndex], changes)
                oldCursor = pair.oldIndex + 1
                newCursor = pair.newIndex + 1
            }
        }
    }

    private fun comparePairedBlocks(
        oldBlock: TextBlock,
        newBlock: TextBlock,
        changes: MutableList<TextChange>,
    ) {
        if (oldBlock.text == newBlock.text) return

        val rawSpans = if (oldBlock.text.length + newBlock.text.length > MAX_TOKENIZED_CHARACTERS) {
            largeCharacterMiddleChange(oldBlock.text, newBlock.text)
        } else {
            val oldTokens = tokenize(oldBlock.text)
            val newTokens = tokenize(newBlock.text)
            if (oldTokens.size + newTokens.size > MAX_TOKEN_MYERS_UNITS) {
                linearMiddleChange(oldTokens, newTokens, oldBlock.text.length, newBlock.text.length)
            } else {
                tokenChanges(oldTokens, newTokens, oldBlock.text.length, newBlock.text.length)
            }
        }
        val spans = mergeAcrossWhitespace(rawSpans, oldBlock.text, newBlock.text)

        if (spans.isEmpty()) return
        val cleaned = if (spans.size == 1 && shouldExpandToWholeBlock(oldBlock, newBlock, spans.single())) {
            listOf(
                SpanChange(
                    old = trimWhitespace(oldBlock.text, TextSpan(0, oldBlock.text.length)),
                    new = trimWhitespace(newBlock.text, TextSpan(0, newBlock.text.length)),
                ),
            )
        } else {
            spans
        }

        cleaned.forEach { span ->
            val previousText = oldBlock.text.substring(span.old.start, span.old.end)
            val currentText = newBlock.text.substring(span.new.start, span.new.end)
            val currentStart = newBlock.start + span.new.start
            val currentEnd = newBlock.start + span.new.end
            changes += createChange(
                currentStart = currentStart,
                currentEnd = currentEnd,
                previousText = previousText,
                currentText = currentText,
                scale = classifyScale(previousText, currentText),
                anchorOffset = currentStart,
            )
        }
    }

    private fun tokenChanges(
        oldTokens: List<SemanticToken>,
        newTokens: List<SemanticToken>,
        oldLength: Int,
        newLength: Int,
    ): List<SpanChange> {
        return diffRegions(oldTokens.map(SemanticToken::text), newTokens.map(SemanticToken::text))
            .map { region ->
                val rawOld = tokenSpan(oldTokens, region.oldStart, region.oldCount, oldLength)
                val rawNew = tokenSpan(newTokens, region.newStart, region.newCount, newLength)
                val trimmedOld = trimWhitespaceFromChange(oldTokens, region.oldStart, region.oldCount, rawOld)
                val trimmedNew = trimWhitespaceFromChange(newTokens, region.newStart, region.newCount, rawNew)
                if (trimmedOld.isEmpty && trimmedNew.isEmpty) {
                    SpanChange(rawOld, rawNew)
                } else {
                    SpanChange(trimmedOld, trimmedNew)
                }
            }
    }

    private fun linearMiddleChange(
        oldTokens: List<SemanticToken>,
        newTokens: List<SemanticToken>,
        oldLength: Int,
        newLength: Int,
    ): List<SpanChange> {
        var prefix = 0
        while (prefix < min(oldTokens.size, newTokens.size) && oldTokens[prefix].text == newTokens[prefix].text) {
            prefix += 1
        }
        var suffix = 0
        while (
            suffix < oldTokens.size - prefix &&
            suffix < newTokens.size - prefix &&
            oldTokens[oldTokens.lastIndex - suffix].text == newTokens[newTokens.lastIndex - suffix].text
        ) {
            suffix += 1
        }
        val oldCount = oldTokens.size - prefix - suffix
        val newCount = newTokens.size - prefix - suffix
        if (oldCount == 0 && newCount == 0) return emptyList()

        val rawOld = tokenSpan(oldTokens, prefix, oldCount, oldLength)
        val rawNew = tokenSpan(newTokens, prefix, newCount, newLength)
        val trimmedOld = trimWhitespaceFromChange(oldTokens, prefix, oldCount, rawOld)
        val trimmedNew = trimWhitespaceFromChange(newTokens, prefix, newCount, rawNew)
        return listOf(
            if (trimmedOld.isEmpty && trimmedNew.isEmpty) {
                SpanChange(rawOld, rawNew)
            } else {
                SpanChange(trimmedOld, trimmedNew)
            },
        )
    }

    private fun largeCharacterMiddleChange(oldText: String, newText: String): List<SpanChange> {
        var prefix = 0
        while (prefix < min(oldText.length, newText.length) && oldText[prefix] == newText[prefix]) {
            prefix += 1
        }
        var suffix = 0
        while (
            suffix < oldText.length - prefix &&
            suffix < newText.length - prefix &&
            oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
        ) {
            suffix += 1
        }
        if (prefix == oldText.length && prefix == newText.length) return emptyList()

        val oldSpan = expandWordBoundary(oldText, TextSpan(prefix, oldText.length - suffix))
        val newSpan = expandWordBoundary(newText, TextSpan(prefix, newText.length - suffix))
        return listOf(
            SpanChange(
                old = trimWhitespace(oldText, oldSpan),
                new = trimWhitespace(newText, newSpan),
            ),
        )
    }

    private fun expandWordBoundary(text: String, span: TextSpan): TextSpan {
        var start = span.start
        var end = span.end
        while (start > 0 && start < text.length &&
            isWordCodePoint(text.codePointBefore(start)) && isWordCodePoint(text.codePointAt(start))
        ) {
            start -= Character.charCount(text.codePointBefore(start))
        }
        while (end > 0 && end < text.length &&
            isWordCodePoint(text.codePointBefore(end)) && isWordCodePoint(text.codePointAt(end))
        ) {
            end += Character.charCount(text.codePointAt(end))
        }
        return TextSpan(start, end)
    }

    private fun shouldExpandToWholeBlock(
        oldBlock: TextBlock,
        newBlock: TextBlock,
        change: SpanChange,
    ): Boolean {
        val oldOutside = oldBlock.text.substring(0, change.old.start) + oldBlock.text.substring(change.old.end)
        val newOutside = newBlock.text.substring(0, change.new.start) + newBlock.text.substring(change.new.end)
        return oldOutside.none(Char::isLetterOrDigit) && newOutside.none(Char::isLetterOrDigit)
    }

    private fun mergeAcrossWhitespace(
        changes: List<SpanChange>,
        oldText: String,
        newText: String,
    ): List<SpanChange> {
        if (changes.size < 2) return changes
        val merged = mutableListOf<SpanChange>()
        changes.forEach { change ->
            val previous = merged.lastOrNull()
            if (previous != null) {
                val oldGap = oldText.substring(previous.old.end, change.old.start)
                val newGap = newText.substring(previous.new.end, change.new.start)
                if (oldGap.isNotEmpty() && newGap.isNotEmpty() &&
                    oldGap.all(Char::isWhitespace) && newGap.all(Char::isWhitespace)
                ) {
                    merged[merged.lastIndex] = SpanChange(
                        old = TextSpan(previous.old.start, change.old.end),
                        new = TextSpan(previous.new.start, change.new.end),
                    )
                    return@forEach
                }
            }
            merged += change
        }
        return merged.map { change ->
            val trimmedOld = trimWhitespace(oldText, change.old)
            val trimmedNew = trimWhitespace(newText, change.new)
            if (trimmedOld.isEmpty && trimmedNew.isEmpty) {
                change
            } else {
                SpanChange(trimmedOld, trimmedNew)
            }
        }
    }

    private fun addWholeBlock(block: TextBlock, changes: MutableList<TextChange>) {
        val span = trimWhitespace(block.text, TextSpan(0, block.text.length))
        if (span.isEmpty) return
        val text = block.text.substring(span.start, span.end)
        val start = block.start + span.start
        changes += createChange(
            currentStart = start,
            currentEnd = block.start + span.end,
            previousText = "",
            currentText = text,
            scale = ChangeScale.ADDED,
            anchorOffset = start,
        )
    }

    private fun addDeletedBlocks(
        blocks: List<TextBlock>,
        previousSource: String,
        anchor: Int,
        currentLength: Int,
        changes: MutableList<TextChange>,
    ) {
        if (blocks.isEmpty()) return
        val previous = previousSource.substring(blocks.first().start, blocks.last().end).trim()
        if (previous.isEmpty()) return
        val safeAnchor = anchor.coerceIn(0, currentLength)
        changes += createChange(
            currentStart = safeAnchor,
            currentEnd = safeAnchor,
            previousText = previous,
            currentText = "",
            scale = ChangeScale.DELETED,
            anchorOffset = safeAnchor,
        )
    }

    private fun alignStableBlocks(oldBlocks: List<TextBlock>, newBlocks: List<TextBlock>): List<BlockMatch> {
        val patience = patienceAnchors(oldBlocks, newBlocks)
        val matches = mutableListOf<BlockMatch>()
        var oldCursor = 0
        var newCursor = 0

        (patience + BlockMatch(oldBlocks.size, newBlocks.size)).forEach { anchor ->
            matches += myersBlockMatches(
                oldBlocks.subList(oldCursor, anchor.oldIndex),
                newBlocks.subList(newCursor, anchor.newIndex),
                oldOffset = oldCursor,
                newOffset = newCursor,
            )
            if (anchor.oldIndex < oldBlocks.size && anchor.newIndex < newBlocks.size) {
                matches += anchor
                oldCursor = anchor.oldIndex + 1
                newCursor = anchor.newIndex + 1
            }
        }
        return matches.distinct().sortedWith(compareBy({ it.oldIndex }, { it.newIndex }))
    }

    private fun patienceAnchors(oldBlocks: List<TextBlock>, newBlocks: List<TextBlock>): List<BlockMatch> {
        val oldPositions = oldBlocks.indices.groupBy { oldBlocks[it].key }
        val newPositions = newBlocks.indices.groupBy { newBlocks[it].key }
        val candidates = oldPositions.mapNotNull { (key, positions) ->
            val new = newPositions[key]
            if (key.isNotEmpty() && positions.size == 1 && new?.size == 1) {
                BlockMatch(positions.single(), new.single())
            } else {
                null
            }
        }.sortedBy(BlockMatch::oldIndex)
        if (candidates.isEmpty()) return emptyList()

        val tails = IntArray(candidates.size)
        val previous = IntArray(candidates.size) { -1 }
        var length = 0
        candidates.indices.forEach { candidateIndex ->
            val newIndex = candidates[candidateIndex].newIndex
            var low = 0
            var high = length
            while (low < high) {
                val middle = (low + high) ushr 1
                if (candidates[tails[middle]].newIndex < newIndex) low = middle + 1 else high = middle
            }
            if (low > 0) previous[candidateIndex] = tails[low - 1]
            tails[low] = candidateIndex
            if (low == length) length += 1
        }

        val result = ArrayDeque<BlockMatch>()
        var index = tails[length - 1]
        while (index >= 0) {
            result.addFirst(candidates[index])
            index = previous[index]
        }
        return result.toList()
    }

    private fun myersBlockMatches(
        oldBlocks: List<TextBlock>,
        newBlocks: List<TextBlock>,
        oldOffset: Int,
        newOffset: Int,
    ): List<BlockMatch> {
        if (oldBlocks.isEmpty() || newBlocks.isEmpty()) return emptyList()
        if (oldBlocks.size + newBlocks.size > MAX_BLOCK_MYERS_UNITS) {
            return linearExactBlockMatches(oldBlocks, newBlocks, oldOffset, newOffset)
        }

        val oldKeys = oldBlocks.map(TextBlock::key)
        val newKeys = newBlocks.map(TextBlock::key)
        val regions = diffRegions(oldKeys, newKeys)
        val matches = mutableListOf<BlockMatch>()
        var oldCursor = 0
        var newCursor = 0
        regions.forEach { region ->
            while (oldCursor < region.oldStart && newCursor < region.newStart) {
                if (oldKeys[oldCursor] == newKeys[newCursor]) {
                    matches += BlockMatch(oldOffset + oldCursor, newOffset + newCursor)
                }
                oldCursor += 1
                newCursor += 1
            }
            oldCursor = region.oldEnd
            newCursor = region.newEnd
        }
        while (oldCursor < oldBlocks.size && newCursor < newBlocks.size) {
            if (oldKeys[oldCursor] == newKeys[newCursor]) {
                matches += BlockMatch(oldOffset + oldCursor, newOffset + newCursor)
            }
            oldCursor += 1
            newCursor += 1
        }
        return matches
    }

    private fun diffRegions(old: List<String>, new: List<String>): List<DiffRegion> {
        val raw = DiffUtils.diff(old, new, MyersDiffWithLinearSpace<String>()).deltas
            .map { delta ->
                DiffRegion(
                    oldStart = delta.source.position,
                    oldEnd = delta.source.position + delta.source.size(),
                    newStart = delta.target.position,
                    newEnd = delta.target.position + delta.target.size(),
                )
            }
            .sortedWith(compareBy({ it.oldStart }, { it.newStart }, { it.oldEnd }, { it.newEnd }))
        if (raw.size < 2) return raw

        val merged = mutableListOf<DiffRegion>()
        raw.forEach { region ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                region.oldStart <= previous.oldEnd &&
                region.newStart <= previous.newEnd
            ) {
                merged[merged.lastIndex] = DiffRegion(
                    oldStart = min(previous.oldStart, region.oldStart),
                    oldEnd = maxOf(previous.oldEnd, region.oldEnd),
                    newStart = min(previous.newStart, region.newStart),
                    newEnd = maxOf(previous.newEnd, region.newEnd),
                )
            } else {
                merged += region
            }
        }
        return merged
    }

    private fun linearExactBlockMatches(
        oldBlocks: List<TextBlock>,
        newBlocks: List<TextBlock>,
        oldOffset: Int,
        newOffset: Int,
    ): List<BlockMatch> {
        var prefix = 0
        while (prefix < min(oldBlocks.size, newBlocks.size) && oldBlocks[prefix].key == newBlocks[prefix].key) {
            prefix += 1
        }
        var suffix = 0
        while (
            suffix < oldBlocks.size - prefix &&
            suffix < newBlocks.size - prefix &&
            oldBlocks[oldBlocks.lastIndex - suffix].key == newBlocks[newBlocks.lastIndex - suffix].key
        ) {
            suffix += 1
        }
        return buildList {
            repeat(prefix) { index -> add(BlockMatch(oldOffset + index, newOffset + index)) }
            repeat(suffix) { distance ->
                add(
                    BlockMatch(
                        oldOffset + oldBlocks.lastIndex - distance,
                        newOffset + newBlocks.lastIndex - distance,
                    ),
                )
            }
        }.sortedBy(BlockMatch::oldIndex)
    }

    private fun pairRewrittenBlocks(oldBlocks: List<TextBlock>, newBlocks: List<TextBlock>): List<BlockMatch> {
        if (oldBlocks.isEmpty() || newBlocks.isEmpty()) return emptyList()
        if (oldBlocks.size == newBlocks.size) {
            return oldBlocks.indices.map { index -> BlockMatch(index, index) }
        }
        val highConfidence = if (oldBlocks.size * newBlocks.size <= MAX_PAIRING_CELLS) {
            similarityAlignment(oldBlocks, newBlocks)
        } else {
            emptyList()
        }
        val result = mutableListOf<BlockMatch>()
        var oldCursor = 0
        var newCursor = 0
        (highConfidence + BlockMatch(oldBlocks.size, newBlocks.size)).forEach { anchor ->
            result += proportionalPairs(
                oldCount = anchor.oldIndex - oldCursor,
                newCount = anchor.newIndex - newCursor,
                oldOffset = oldCursor,
                newOffset = newCursor,
                oldBlocks = oldBlocks,
                newBlocks = newBlocks,
            )
            if (anchor.oldIndex < oldBlocks.size && anchor.newIndex < newBlocks.size) {
                result += anchor
                oldCursor = anchor.oldIndex + 1
                newCursor = anchor.newIndex + 1
            }
        }
        return result.distinct().sortedWith(compareBy({ it.oldIndex }, { it.newIndex }))
    }

    private fun similarityAlignment(oldBlocks: List<TextBlock>, newBlocks: List<TextBlock>): List<BlockMatch> {
        val rows = oldBlocks.size
        val columns = newBlocks.size
        val scores = Array(rows + 1) { DoubleArray(columns + 1) }
        val choices = Array(rows + 1) { ByteArray(columns + 1) }
        for (row in 1..rows) {
            for (column in 1..columns) {
                val skipOld = scores[row - 1][column]
                val skipNew = scores[row][column - 1]
                val similarity = blockSimilarity(oldBlocks[row - 1], newBlocks[column - 1])
                val match = if (similarity >= MIN_BLOCK_PAIR_SIMILARITY) {
                    scores[row - 1][column - 1] + similarity
                } else {
                    Double.NEGATIVE_INFINITY
                }
                when {
                    match > skipOld && match > skipNew -> {
                        scores[row][column] = match
                        choices[row][column] = 3
                    }
                    skipOld >= skipNew -> {
                        scores[row][column] = skipOld
                        choices[row][column] = 1
                    }
                    else -> {
                        scores[row][column] = skipNew
                        choices[row][column] = 2
                    }
                }
            }
        }

        val reversed = mutableListOf<BlockMatch>()
        var row = rows
        var column = columns
        while (row > 0 && column > 0) {
            when (choices[row][column].toInt()) {
                3 -> {
                    reversed += BlockMatch(row - 1, column - 1)
                    row -= 1
                    column -= 1
                }
                1 -> row -= 1
                else -> column -= 1
            }
        }
        return reversed.asReversed()
    }

    private fun proportionalPairs(
        oldCount: Int,
        newCount: Int,
        oldOffset: Int,
        newOffset: Int,
        oldBlocks: List<TextBlock>,
        newBlocks: List<TextBlock>,
    ): List<BlockMatch> {
        if (oldCount == 0 || newCount == 0) return emptyList()
        if (oldCount == 1) {
            val bestNew = (0 until newCount).maxByOrNull { index ->
                blockSimilarity(oldBlocks[oldOffset], newBlocks[newOffset + index])
            } ?: 0
            return listOf(BlockMatch(oldOffset, newOffset + bestNew))
        }
        if (newCount == 1) {
            val bestOld = (0 until oldCount).maxByOrNull { index ->
                blockSimilarity(oldBlocks[oldOffset + index], newBlocks[newOffset])
            } ?: 0
            return listOf(BlockMatch(oldOffset + bestOld, newOffset))
        }
        return if (oldCount <= newCount) {
            (0 until oldCount).map { index ->
                val mapped = (index.toDouble() * (newCount - 1) / (oldCount - 1)).roundToInt()
                BlockMatch(oldOffset + index, newOffset + mapped)
            }
        } else {
            (0 until newCount).map { index ->
                val mapped = (index.toDouble() * (oldCount - 1) / (newCount - 1)).roundToInt()
                BlockMatch(oldOffset + mapped, newOffset + index)
            }
        }
    }

    private fun blockSimilarity(oldBlock: TextBlock, newBlock: TextBlock): Double {
        val oldTerms = semanticTerms(oldBlock.text)
        val newTerms = semanticTerms(newBlock.text)
        if (oldTerms.isEmpty() || newTerms.isEmpty()) return 0.0
        val oldCounts = oldTerms.groupingBy { it }.eachCount()
        val newCounts = newTerms.groupingBy { it }.eachCount()
        val common = oldCounts.entries.sumOf { (term, count) -> min(count, newCounts[term] ?: 0) }
        val dice = 2.0 * common / (oldTerms.size + newTerms.size)
        val edgeBonus = (if (oldTerms.first() == newTerms.first()) 0.05 else 0.0) +
            (if (oldTerms.last() == newTerms.last()) 0.05 else 0.0)
        return (dice + edgeBonus).coerceAtMost(1.0)
    }

    private fun splitMarkdownBlocks(text: String): List<TextBlock> {
        if (text.isEmpty()) return emptyList()
        val blocks = mutableListOf<TextBlock>()
        var cursor = 0
        var paragraphStart = -1
        var paragraphEnd = -1
        var fenceStart = -1
        var fenceMarker = ""

        fun flushParagraph() {
            if (paragraphStart >= 0 && paragraphEnd > paragraphStart) {
                blocks += createBlock(text, paragraphStart, paragraphEnd)
            }
            paragraphStart = -1
            paragraphEnd = -1
        }

        while (cursor < text.length) {
            val lineStart = cursor
            val newline = text.indexOf('\n', cursor)
            val rawEnd = if (newline < 0) text.length else newline
            val lineEnd = if (rawEnd > lineStart && text[rawEnd - 1] == '\r') rawEnd - 1 else rawEnd
            val nextLine = if (newline < 0) text.length else newline + 1
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trim()

            if (fenceStart >= 0) {
                if (trimmed.startsWith(fenceMarker)) {
                    blocks += createBlock(text, fenceStart, lineEnd)
                    fenceStart = -1
                    fenceMarker = ""
                }
            } else if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushParagraph()
                fenceStart = lineStart
                fenceMarker = trimmed.take(3)
            } else if (trimmed.isEmpty()) {
                flushParagraph()
            } else if (structuralLine.containsMatchIn(trimmed)) {
                flushParagraph()
                blocks += createBlock(text, lineStart, lineEnd)
            } else {
                if (paragraphStart < 0) paragraphStart = lineStart
                paragraphEnd = lineEnd
            }
            cursor = nextLine
        }
        if (fenceStart >= 0) blocks += createBlock(text, fenceStart, text.length)
        flushParagraph()
        return blocks
    }

    private fun createBlock(source: String, start: Int, end: Int): TextBlock {
        val text = source.substring(start, end)
        val key = Normalizer.normalize(text, Normalizer.Form.NFC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
        return TextBlock(text = text, start = start, end = end, key = key)
    }

    private fun tokenize(text: String): List<SemanticToken> {
        val result = mutableListOf<SemanticToken>()
        var index = 0
        while (index < text.length) {
            val start = index
            val codePoint = text.codePointAt(index)
            when {
                Character.isWhitespace(codePoint) -> {
                    index += Character.charCount(codePoint)
                    while (index < text.length && Character.isWhitespace(text.codePointAt(index))) {
                        index += Character.charCount(text.codePointAt(index))
                    }
                    result += SemanticToken(text.substring(start, index), start, index, TokenKind.WHITESPACE)
                }
                isWordCodePoint(codePoint) -> {
                    index += Character.charCount(codePoint)
                    while (index < text.length && isWordCodePoint(text.codePointAt(index))) {
                        index += Character.charCount(text.codePointAt(index))
                    }
                    result += SemanticToken(text.substring(start, index), start, index, TokenKind.WORD)
                }
                else -> {
                    index += Character.charCount(codePoint)
                    var joinNext = false
                    while (index < text.length) {
                        val next = text.codePointAt(index)
                        if (isEmojiModifier(next) || Character.getType(next) == Character.NON_SPACING_MARK.toInt()) {
                            index += Character.charCount(next)
                        } else if (next == ZERO_WIDTH_JOINER) {
                            index += Character.charCount(next)
                            joinNext = true
                        } else if (joinNext) {
                            index += Character.charCount(next)
                            joinNext = false
                        } else {
                            break
                        }
                    }
                    result += SemanticToken(text.substring(start, index), start, index, TokenKind.SYMBOL)
                }
            }
        }
        return result
    }

    private fun semanticTerms(text: String): List<String> = tokenize(text)
        .filterNot { it.kind == TokenKind.WHITESPACE }
        .map { Normalizer.normalize(it.text, Normalizer.Form.NFC).lowercase(Locale.ROOT) }

    private fun isWordCodePoint(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) ||
            codePoint == '_'.code ||
            Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(codePoint) == Character.COMBINING_SPACING_MARK.toInt()

    private fun isEmojiModifier(codePoint: Int): Boolean =
        codePoint in 0x1F3FB..0x1F3FF || codePoint in 0xFE00..0xFE0F

    private fun tokenSpan(
        tokens: List<SemanticToken>,
        position: Int,
        count: Int,
        textLength: Int,
    ): TextSpan {
        if (count == 0) {
            val offset = tokens.getOrNull(position)?.start ?: textLength
            return TextSpan(offset, offset)
        }
        return TextSpan(tokens[position].start, tokens[position + count - 1].end)
    }

    private fun trimWhitespaceFromChange(
        tokens: List<SemanticToken>,
        position: Int,
        count: Int,
        fallback: TextSpan,
    ): TextSpan {
        if (count == 0) return fallback
        var first = position
        var last = position + count - 1
        while (first <= last && tokens[first].kind == TokenKind.WHITESPACE) first += 1
        while (last >= first && tokens[last].kind == TokenKind.WHITESPACE) last -= 1
        return if (first > last) fallback else TextSpan(tokens[first].start, tokens[last].end)
    }

    private fun trimWhitespace(text: String, span: TextSpan): TextSpan {
        var start = span.start
        var end = span.end
        while (start < end && text[start].isWhitespace()) start += 1
        while (end > start && text[end - 1].isWhitespace()) end -= 1
        return TextSpan(start, end)
    }

    private fun classifyScale(previous: String, current: String): ChangeScale {
        if (previous.isEmpty()) return ChangeScale.ADDED
        if (current.isEmpty()) return ChangeScale.DELETED
        if (previous.length + current.length > 10_000) return ChangeScale.PARAGRAPH
        val wordCount = maxOf(
            tokenize(previous).count { it.kind == TokenKind.WORD },
            tokenize(current).count { it.kind == TokenKind.WORD },
        )
        return when {
            wordCount <= 1 && '\n' !in previous && '\n' !in current -> ChangeScale.WORD
            wordCount <= 4 && '\n' !in previous && '\n' !in current -> ChangeScale.PHRASE
            wordCount <= 12 && '\n' !in previous && '\n' !in current -> ChangeScale.SENTENCE
            else -> ChangeScale.PARAGRAPH
        }
    }

    private fun normalizeChanges(changes: List<TextChange>, currentLength: Int): List<TextChange> = changes
        .asSequence()
        .filter { it.previousText.isNotEmpty() || it.currentText.isNotEmpty() }
        .map { change ->
            change.copy(
                currentStart = change.currentStart.coerceIn(0, currentLength),
                currentEnd = change.currentEnd.coerceIn(change.currentStart.coerceIn(0, currentLength), currentLength),
                anchorOffset = change.anchorOffset.coerceIn(0, currentLength),
            )
        }
        .distinctBy { listOf(it.currentStart, it.currentEnd, it.previousText, it.currentText) }
        .sortedWith(
            compareBy<TextChange> { it.currentStart }
                .thenBy { if (it.isDeletion) 0 else 1 }
                .thenBy { it.currentEnd },
        )
        .toList()

    private fun whitespaceOnlyChange(previous: String, current: String): List<TextChange> {
        if (current.isEmpty()) {
            return listOf(createChange(0, 0, previous, "", ChangeScale.DELETED, 0))
        }
        return listOf(
            createChange(
                currentStart = 0,
                currentEnd = current.length,
                previousText = previous,
                currentText = current,
                scale = if (previous.isEmpty()) ChangeScale.ADDED else ChangeScale.PHRASE,
                anchorOffset = 0,
            ),
        )
    }

    private fun createChange(
        currentStart: Int,
        currentEnd: Int,
        previousText: String,
        currentText: String,
        scale: ChangeScale,
        anchorOffset: Int,
    ): TextChange = TextChange(
        id = listOf(
            scale.name,
            currentStart,
            currentEnd,
            previousText.hashCode(),
            currentText.hashCode(),
        ).joinToString(":"),
        currentStart = currentStart,
        currentEnd = currentEnd,
        previousText = previousText,
        currentText = currentText,
        scale = scale,
        anchorOffset = anchorOffset,
    )

    private data class TextBlock(
        val text: String,
        val start: Int,
        val end: Int,
        val key: String,
    )

    private data class BlockMatch(val oldIndex: Int, val newIndex: Int)

    private data class SemanticToken(
        val text: String,
        val start: Int,
        val end: Int,
        val kind: TokenKind,
    )

    private enum class TokenKind { WORD, WHITESPACE, SYMBOL }

    private data class TextSpan(val start: Int, val end: Int) {
        val isEmpty: Boolean get() = start == end
    }

    private data class SpanChange(val old: TextSpan, val new: TextSpan)

    private data class DiffRegion(
        val oldStart: Int,
        val oldEnd: Int,
        val newStart: Int,
        val newEnd: Int,
    ) {
        val oldCount: Int get() = oldEnd - oldStart
        val newCount: Int get() = newEnd - newStart
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
}
