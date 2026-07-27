package mom.cosmism.textory.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mom.cosmism.textory.diff.TextChange
import mom.cosmism.textory.ui.theme.LightTextoryColors
import mom.cosmism.textory.ui.theme.TextoryColors
import mom.cosmism.textory.ui.theme.TextoryPalette

internal const val RICH_TEXT_URL_TAG = "textory-url"
private const val EDITOR_DIFF_HIGHLIGHT_ALPHA = 0.55f

private enum class MarkdownBlockKind {
    PARAGRAPH,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    HEADING_SMALL,
    BULLET,
    NUMBERED,
    CHECKBOX_OPEN,
    CHECKBOX_DONE,
    QUOTE,
    CODE,
    DIVIDER,
}

private data class MarkdownBlock(
    val id: Int,
    val rawStart: Int,
    val rawEnd: Int,
    val kind: MarkdownBlockKind,
    val prefix: String? = null,
    val text: AnnotatedString = AnnotatedString(""),
    val rawOffsets: IntArray = IntArray(0),
) {
    val stableKey: String = listOf(
        kind.name,
        rawStart,
        rawEnd,
        text.text.hashCode(),
        rawOffsets.contentHashCode(),
    ).joinToString(":")
}

data class ChangeNavigationRequest(
    val offset: Int,
    val token: Long,
)

@Composable
fun MarkdownPreview(
    markdown: String,
    changes: List<TextChange>,
    fontSizeSp: Float,
    onChangeTapped: (TextChange) -> Unit,
    navigationRequest: ChangeNavigationRequest? = null,
    bottomOverlayPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val palette = TextoryPalette.current
    val blocks = remember(markdown, palette) { MarkdownParser.parse(markdown, palette) }
    val scrollState = rememberScrollState()
    val blockKeys = remember(blocks) { blocks.map(MarkdownBlock::stableKey) }
    val blockRequesters = remember(blockKeys) { List(blocks.size) { BringIntoViewRequester() } }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val richDocument = remember(blocks, fontSizeSp) { richClipboardDocument(blocks, fontSizeSp) }
    val richTextClipboard = remember(clipboard, context, richDocument) {
        RichTextClipboard(
            delegate = clipboard,
            context = context,
            richDocument = richDocument,
        )
    }
    LaunchedEffect(navigationRequest?.token, blocks, bottomOverlayPadding) {
        val request = navigationRequest ?: return@LaunchedEffect
        if (blocks.isEmpty()) return@LaunchedEffect
        val containing = blocks.indexOfFirst { block -> request.offset in block.rawStart..block.rawEnd }
        val target = if (containing >= 0) {
            containing
        } else {
            blocks.indexOfLast { block -> block.rawStart <= request.offset }.coerceAtLeast(0)
        }
        blockRequesters.getOrNull(target)?.bringIntoView()
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomOverlayPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        val readerWidth = maxWidth.coerceAtMost(720.dp)
        if (blocks.isEmpty()) {
            Text(
                text = "Документ пока пуст",
                color = TextoryPalette.InkMuted,
                fontSize = fontSizeSp.sp,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            CompositionLocalProvider(LocalClipboard provides richTextClipboard) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .width(readerWidth)
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        blocks.forEachIndexed { index, block ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(blockRequesters[index]),
                            ) {
                                MarkdownBlockView(
                                    block = block,
                                    changes = changes,
                                    fontSizeSp = fontSizeSp,
                                    onChangeTapped = onChangeTapped,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class RichTextClipboard(
    private val delegate: Clipboard,
    private val context: Context,
    private val richDocument: AnnotatedString,
) : Clipboard by delegate {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (clipEntry == null) {
            delegate.setClipEntry(null)
            return
        }
        val item = clipEntry.clipData.getItemAt(0)
        val plainText = item.text?.toString() ?: item.coerceToText(context).toString()
        val richSelection = richSelectionForPlainText(richDocument, plainText)
        val richClip = ClipData(
            "Textory rich text",
            arrayOf(
                ClipDescription.MIMETYPE_TEXT_PLAIN,
                ClipDescription.MIMETYPE_TEXT_HTML,
            ),
            ClipData.Item(
                plainText,
                selectedTextsToHtml(listOf(richSelection)),
            ),
        )
        delegate.setClipEntry(richClip.toClipEntry())
    }
}

internal fun richSelectionForPlainText(
    richDocument: AnnotatedString,
    plainText: String,
): AnnotatedString {
    if (plainText.isEmpty()) return AnnotatedString("")
    val start = richDocument.text.indexOf(plainText)
    return if (start >= 0) {
        richDocument.subSequence(start, start + plainText.length)
    } else {
        AnnotatedString(plainText)
    }
}

internal fun selectedTextsToHtml(texts: List<AnnotatedString>): String =
    texts.joinToString(separator = "<br>") { it.toRichHtml() }

private fun AnnotatedString.toRichHtml(): String {
    if (isEmpty()) return ""
    val links = getStringAnnotations(tag = RICH_TEXT_URL_TAG, start = 0, end = length)
    val boundaries = buildSet {
        add(0)
        add(length)
        spanStyles.forEach { range ->
            add(range.start.coerceIn(0, length))
            add(range.end.coerceIn(0, length))
        }
        links.forEach { range ->
            add(range.start.coerceIn(0, length))
            add(range.end.coerceIn(0, length))
        }
    }.sorted()
    return boundaries.zipWithNext().joinToString(separator = "") { (start, end) ->
        var style = SpanStyle()
        spanStyles
            .filter { range -> range.start <= start && range.end >= end }
            .forEach { range -> style = style.merge(range.item) }
        val url = links.firstOrNull { range -> range.start <= start && range.end >= end }?.item
        var segment = text.substring(start, end).escapeHtml().replace("\n", "<br>")
        if (style.fontFamily == FontFamily.Monospace) segment = "<code>$segment</code>"
        if ((style.fontWeight?.weight ?: 0) >= FontWeight.SemiBold.weight) {
            segment = "<strong>$segment</strong>"
        }
        if (style.fontStyle == FontStyle.Italic) segment = "<em>$segment</em>"
        if (style.textDecoration == TextDecoration.Underline && url == null) {
            segment = "<u>$segment</u>"
        }
        if (style.fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) {
            segment = "<span style=\"font-size:${style.fontSize.value}pt\">$segment</span>"
        }
        if (url != null) segment = "<a href=\"${url.escapeHtml()}\">$segment</a>"
        segment
    }
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private data class MarkdownTypography(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val fontFamily: FontFamily,
    val fontStyle: FontStyle,
)

private fun markdownTypography(
    kind: MarkdownBlockKind,
    baseFontSizeSp: Float,
): MarkdownTypography {
    val scale = baseFontSizeSp / 17f
    return MarkdownTypography(
        fontSize = when (kind) {
            MarkdownBlockKind.HEADING_1 -> (28f * scale).sp
            MarkdownBlockKind.HEADING_2 -> (24f * scale).sp
            MarkdownBlockKind.HEADING_3 -> (21f * scale).sp
            MarkdownBlockKind.HEADING_SMALL -> (18f * scale).sp
            MarkdownBlockKind.CODE -> (15f * scale).sp
            else -> baseFontSizeSp.sp
        },
        lineHeight = when (kind) {
            MarkdownBlockKind.HEADING_1 -> (34f * scale).sp
            MarkdownBlockKind.HEADING_2 -> (30f * scale).sp
            MarkdownBlockKind.HEADING_3 -> (27f * scale).sp
            MarkdownBlockKind.CODE -> (23f * scale).sp
            else -> (27f * scale).sp
        },
        fontWeight = when (kind) {
            MarkdownBlockKind.HEADING_1,
            MarkdownBlockKind.HEADING_2,
            MarkdownBlockKind.HEADING_3,
            MarkdownBlockKind.HEADING_SMALL,
            -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        fontFamily = if (kind == MarkdownBlockKind.CODE) FontFamily.Monospace else FontFamily.SansSerif,
        fontStyle = if (kind == MarkdownBlockKind.QUOTE) FontStyle.Italic else FontStyle.Normal,
    )
}

private fun styledMarkdownBlockText(
    block: MarkdownBlock,
    fontSizeSp: Float,
): AnnotatedString {
    val typography = markdownTypography(block.kind, fontSizeSp)
    return buildAnnotatedString {
        pushStyle(
            SpanStyle(
                fontSize = typography.fontSize,
                fontWeight = typography.fontWeight,
                fontFamily = typography.fontFamily,
                fontStyle = typography.fontStyle,
            ),
        )
        append(block.text)
        pop()
    }
}

private fun richClipboardDocument(
    blocks: List<MarkdownBlock>,
    fontSizeSp: Float,
): AnnotatedString = buildAnnotatedString {
    val visibleBlocks = blocks.filterNot { it.kind == MarkdownBlockKind.DIVIDER }
    visibleBlocks.forEachIndexed { index, block ->
        block.prefix?.takeIf(String::isNotEmpty)?.let { prefix ->
            append(prefix)
            append(" ")
        }
        append(styledMarkdownBlockText(block, fontSizeSp))
        if (index < visibleBlocks.lastIndex) append("\n")
    }
}

internal fun richClipboardDocumentForMarkdown(
    markdown: String,
    fontSizeSp: Float = 17f,
): AnnotatedString = richClipboardDocument(MarkdownParser.parse(markdown, LightTextoryColors), fontSizeSp)

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    changes: List<TextChange>,
    fontSizeSp: Float,
    onChangeTapped: (TextChange) -> Unit,
) {
    if (block.kind == MarkdownBlockKind.DIVIDER) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(TextoryPalette.Border),
        )
        return
    }

    val highlights = remember(block, changes) { blockHighlights(block, changes) }
    val deletion = changes.firstOrNull { change ->
        change.isDeletion && change.anchorOffset in block.rawStart..block.rawEnd
    }
    val content: @Composable () -> Unit = {
        RenderedText(
            block = block,
            highlights = highlights,
            fontSizeSp = fontSizeSp,
            onChangeTapped = onChangeTapped,
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        when (block.kind) {
            MarkdownBlockKind.BULLET,
            MarkdownBlockKind.NUMBERED,
            MarkdownBlockKind.CHECKBOX_OPEN,
            MarkdownBlockKind.CHECKBOX_DONE,
            -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = block.prefix.orEmpty(),
                    color = if (block.kind == MarkdownBlockKind.CHECKBOX_DONE) {
                        TextoryPalette.Green
                    } else {
                        TextoryPalette.InkMuted
                    },
                    fontSize = (fontSizeSp * 16f / 17f).sp,
                    lineHeight = (fontSizeSp * 27f / 17f).sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) { content() }
            }

            MarkdownBlockKind.QUOTE -> Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .background(TextoryPalette.AccentHighlight, RoundedCornerShape(2.dp)),
                )
                Box(modifier = Modifier.padding(start = 12.dp)) { content() }
            }

            MarkdownBlockKind.CODE -> Surface(
                color = TextoryPalette.Toolbar,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
            }

            else -> content()
        }

        deletion?.let { change ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
                    .clickable { onChangeTapped(change) }
                    .semantics { contentDescription = "Показать удалённый фрагмент" },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height(20.dp)
                        .background(TextoryPalette.RedHighlight, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun RenderedText(
    block: MarkdownBlock,
    highlights: List<BlockHighlight>,
    fontSizeSp: Float,
    onChangeTapped: (TextChange) -> Unit,
) {
    val highlightColor = TextoryPalette.GreenHighlight
    var layout by remember(block.stableKey) { mutableStateOf<TextLayoutResult?>(null) }
    val displayRanges = remember(highlights) { highlights.map(BlockHighlight::display) }
    val doubleTapTracker = remember(block.stableKey, highlights) { PassiveDoubleTapTracker() }
    val density = LocalDensity.current
    val hitHorizontalInset = with(density) { 2.dp.toPx() }
    val hitVerticalInset = with(density) { 1.dp.toPx() }
    fun resolveChangeAt(position: Offset): TextChange? {
        val textLayout = layout ?: return null
        val displayOffset = textLayout.getOffsetForPosition(position)
        val geometryHits = highlights.filter { highlight ->
            textHighlightRects(
                layout = textLayout,
                range = highlight.display,
                horizontalInset = hitHorizontalInset,
                verticalInset = hitVerticalInset,
            ).any { rect -> rect.contains(position) }
        }
        val exactHits = geometryHits.filter { highlight ->
            displayOffset >= highlight.display.start && displayOffset < highlight.display.end
        }
        val candidates = (exactHits.ifEmpty { geometryHits }).map { highlight ->
            ChangeHitCandidate(
                change = highlight.change,
                visibleSpanLength = highlight.display.end - highlight.display.start,
            )
        }
        return ChangeHitResolver.mostSpecific(candidates)
    }
    val modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
            drawRoundedTextHighlights(layout, displayRanges, highlightColor)
        }
        .observeTapWithoutConsuming(block.stableKey, highlights) { position ->
            resolveChangeAt(position)?.let(onChangeTapped)
        }
        .observeDoubleTapWithoutConsuming(doubleTapTracker, block.stableKey, highlights) { first, second ->
            val midpoint = Offset(
                x = (first.x + second.x) / 2f,
                y = (first.y + second.y) / 2f,
            )
            val votes = listOf(first, second, midpoint).map(::resolveChangeAt)
            ChangeHitResolver.fromDoubleTapVotes(votes)?.let(onChangeTapped)
        }

    val typography = remember(block.kind, fontSizeSp) { markdownTypography(block.kind, fontSizeSp) }
    val richText = remember(block.text, typography) { styledMarkdownBlockText(block, fontSizeSp) }

    Text(
        text = richText,
        modifier = modifier,
        color = when (block.kind) {
            MarkdownBlockKind.QUOTE -> TextoryPalette.InkMuted
            else -> TextoryPalette.Ink
        },
        fontSize = typography.fontSize,
        lineHeight = typography.lineHeight,
        fontWeight = typography.fontWeight,
        fontFamily = typography.fontFamily,
        fontStyle = typography.fontStyle,
        onTextLayout = { layout = it },
    )
}

private fun blockHighlights(block: MarkdownBlock, changes: List<TextChange>): List<BlockHighlight> =
    mapChangesToDisplayRanges(block.rawOffsets, changes)

private object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val checkbox = Regex("^[-*]\\s+\\[([ xX])]\\s+(.*)$")
    private val numbered = Regex("^(\\d+[.)])\\s+(.*)$")
    private val bullet = Regex("^[-*+]\\s+(.*)$")
    private val divider = Regex("^(---+|___+|\\*\\*\\*+)$")

    fun parse(markdown: String, colors: TextoryColors): List<MarkdownBlock> {
        val result = mutableListOf<MarkdownBlock>()
        var cursor = 0
        var id = 0
        while (cursor < markdown.length) {
            val lineEnd = markdown.indexOf('\n', cursor).let { if (it == -1) markdown.length else it }
            val line = markdown.substring(cursor, lineEnd)
            if (line.trimStart().startsWith("```")) {
                val codeStart = if (lineEnd < markdown.length) lineEnd + 1 else lineEnd
                var closingStart = markdown.length
                var scan = codeStart
                var closingEnd = markdown.length
                while (scan < markdown.length) {
                    val scanEnd = markdown.indexOf('\n', scan).let { if (it == -1) markdown.length else it }
                    if (markdown.substring(scan, scanEnd).trimStart().startsWith("```")) {
                        closingStart = scan
                        closingEnd = if (scanEnd < markdown.length) scanEnd + 1 else scanEnd
                        break
                    }
                    scan = if (scanEnd < markdown.length) scanEnd + 1 else markdown.length
                }
                val contentEnd = closingStart.let { end ->
                    if (end > codeStart && markdown[end - 1] == '\n') end - 1 else end
                }
                val inline = plainInline(markdown.substring(codeStart, contentEnd), codeStart)
                result += MarkdownBlock(
                    id = id++,
                    rawStart = cursor,
                    rawEnd = closingEnd,
                    kind = MarkdownBlockKind.CODE,
                    text = inline.first,
                    rawOffsets = inline.second,
                )
                cursor = closingEnd
                continue
            }

            val trimmedStart = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) line.length else it }
            val visible = line.substring(trimmedStart)
            if (visible.isNotEmpty()) {
                val absolute = cursor + trimmedStart
                val block = when {
                    visible.matches(divider) -> MarkdownBlock(
                        id = id++, rawStart = cursor, rawEnd = lineEnd, kind = MarkdownBlockKind.DIVIDER,
                    )
                    heading.matches(visible) -> {
                        val match = heading.matchEntire(visible)!!
                        val hashes = match.groupValues[1].length
                        val content = match.groupValues[2]
                        val contentStart = absolute + match.groups[2]!!.range.first
                        fromInline(id++, cursor, lineEnd, headingKind(hashes), content, contentStart, colors = colors)
                    }
                    checkbox.matches(visible) -> {
                        val match = checkbox.matchEntire(visible)!!
                        val checked = match.groupValues[1].isNotBlank()
                        val content = match.groupValues[2]
                        val contentStart = absolute + match.groups[2]!!.range.first
                        fromInline(
                            id++, cursor, lineEnd,
                            if (checked) MarkdownBlockKind.CHECKBOX_DONE else MarkdownBlockKind.CHECKBOX_OPEN,
                            content, contentStart, if (checked) "☑" else "☐", colors,
                        )
                    }
                    numbered.matches(visible) -> {
                        val match = numbered.matchEntire(visible)!!
                        val content = match.groupValues[2]
                        val contentStart = absolute + match.groups[2]!!.range.first
                        fromInline(
                            id++, cursor, lineEnd, MarkdownBlockKind.NUMBERED,
                            content, contentStart, match.groupValues[1], colors,
                        )
                    }
                    bullet.matches(visible) -> {
                        val match = bullet.matchEntire(visible)!!
                        val content = match.groupValues[1]
                        val contentStart = absolute + match.groups[1]!!.range.first
                        fromInline(id++, cursor, lineEnd, MarkdownBlockKind.BULLET, content, contentStart, "•", colors)
                    }
                    visible.startsWith("> ") -> fromInline(
                        id++, cursor, lineEnd, MarkdownBlockKind.QUOTE,
                        visible.removePrefix("> "), absolute + 2, colors = colors,
                    )
                    else -> fromInline(
                        id++, cursor, lineEnd, MarkdownBlockKind.PARAGRAPH, visible, absolute, colors = colors,
                    )
                }
                result += block
            }
            cursor = if (lineEnd < markdown.length) lineEnd + 1 else markdown.length
        }
        return result
    }

    private fun headingKind(level: Int): MarkdownBlockKind = when (level) {
        1 -> MarkdownBlockKind.HEADING_1
        2 -> MarkdownBlockKind.HEADING_2
        3 -> MarkdownBlockKind.HEADING_3
        else -> MarkdownBlockKind.HEADING_SMALL
    }

    private fun fromInline(
        id: Int,
        rawStart: Int,
        rawEnd: Int,
        kind: MarkdownBlockKind,
        content: String,
        contentStart: Int,
        prefix: String? = null,
        colors: TextoryColors,
    ): MarkdownBlock {
        val inline = inlineMarkdown(content, contentStart, colors)
        return MarkdownBlock(id, rawStart, rawEnd, kind, prefix, inline.first, inline.second)
    }

    private fun plainInline(content: String, contentStart: Int): Pair<AnnotatedString, IntArray> =
        AnnotatedString(content) to IntArray(content.length) { contentStart + it }

    private fun inlineMarkdown(
        content: String,
        contentStart: Int,
        colors: TextoryColors,
    ): Pair<AnnotatedString, IntArray> {
        val builder = AnnotatedString.Builder()
        val offsets = mutableListOf<Int>()

        fun appendRange(
            start: Int,
            end: Int,
            style: SpanStyle? = null,
            url: String? = null,
        ) {
            val outputStart = builder.length
            builder.append(content.substring(start, end))
            for (index in start until end) offsets += contentStart + index
            if (style != null && outputStart < builder.length) {
                builder.addStyle(style, outputStart, builder.length)
            }
            if (url != null && outputStart < builder.length) {
                builder.addStringAnnotation(RICH_TEXT_URL_TAG, url, outputStart, builder.length)
            }
        }

        var index = 0
        while (index < content.length) {
            when {
                content.startsWith("**", index) -> {
                    val close = content.indexOf("**", index + 2)
                    if (close > index + 2) {
                        appendRange(index + 2, close, SpanStyle(fontWeight = FontWeight.Bold))
                        index = close + 2
                    } else {
                        appendRange(index, index + 1)
                        index++
                    }
                }
                content[index] == '`' -> {
                    val close = content.indexOf('`', index + 1)
                    if (close > index + 1) {
                        appendRange(
                            index + 1,
                            close,
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colors.accent,
                            ),
                        )
                        index = close + 1
                    } else {
                        appendRange(index, index + 1)
                        index++
                    }
                }
                content[index] == '[' -> {
                    val labelEnd = content.indexOf("](", index + 1)
                    val linkEnd = if (labelEnd != -1) content.indexOf(')', labelEnd + 2) else -1
                    if (labelEnd > index + 1 && linkEnd > labelEnd) {
                        appendRange(
                            index + 1,
                            labelEnd,
                            SpanStyle(
                                color = colors.accent,
                                textDecoration = TextDecoration.Underline,
                            ),
                            url = content.substring(labelEnd + 2, linkEnd),
                        )
                        index = linkEnd + 1
                    } else {
                        appendRange(index, index + 1)
                        index++
                    }
                }
                content[index] == '*' || content[index] == '_' -> {
                    val marker = content[index]
                    val close = content.indexOf(marker, index + 1)
                    if (close > index + 1) {
                        appendRange(index + 1, close, SpanStyle(fontStyle = FontStyle.Italic))
                        index = close + 1
                    } else {
                        appendRange(index, index + 1)
                        index++
                    }
                }
                content[index] == '\\' && index + 1 < content.length -> {
                    appendRange(index + 1, index + 2)
                    index += 2
                }
                else -> {
                    appendRange(index, index + 1)
                    index++
                }
            }
        }
        return builder.toAnnotatedString() to offsets.toIntArray()
    }
}

private data class EditorStyleRange(
    val style: SpanStyle,
    val start: Int,
    val end: Int,
)

private val editorHeading = Regex("(?m)^(#{1,6})(\\s+)(.*)$")
private val editorBold = Regex("\\*\\*([^*\\n]+)\\*\\*")
private val editorItalic = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")
private val editorCode = Regex("`([^`\\n]+)`")
private val editorLink = Regex("\\[([^]\\n]+)]\\(([^)\\n]+)\\)")
private val editorLinePrefix = Regex("(?m)^(?:[-*+] |\\d+[.)] |[-*] \\[[ xX]] |> )")

private fun markdownEditorStyleRanges(
    source: String,
    baseFontSizeSp: Float,
    colors: TextoryColors,
): List<EditorStyleRange> = buildList {
    editorHeading.findAll(source).forEach { match ->
        val level = match.groupValues[1].length
        add(
            EditorStyleRange(
                SpanStyle(color = colors.inkMuted, fontWeight = FontWeight.Medium),
                match.groups[1]!!.range.first,
                match.groups[2]!!.range.last + 1,
            ),
        )
        add(
            EditorStyleRange(
                SpanStyle(
                    fontSize = editorHeadingFontSizeSp(baseFontSizeSp, level).sp,
                    fontWeight = FontWeight.Bold,
                ),
                match.range.first,
                match.range.last + 1,
            ),
        )
    }
    editorBold.findAll(source).forEach { match ->
        add(EditorStyleRange(SpanStyle(fontWeight = FontWeight.Bold), match.groups[1]!!.range.first, match.groups[1]!!.range.last + 1))
    }
    editorItalic.findAll(source).forEach { match ->
        add(EditorStyleRange(SpanStyle(fontStyle = FontStyle.Italic), match.groups[1]!!.range.first, match.groups[1]!!.range.last + 1))
    }
    editorCode.findAll(source).forEach { match ->
        add(
            EditorStyleRange(
                SpanStyle(fontFamily = FontFamily.Monospace, color = colors.accent),
                match.groups[1]!!.range.first,
                match.groups[1]!!.range.last + 1,
            ),
        )
    }
    editorLink.findAll(source).forEach { match ->
        add(
            EditorStyleRange(
                SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                match.groups[1]!!.range.first,
                match.groups[1]!!.range.last + 1,
            ),
        )
        add(
            EditorStyleRange(
                SpanStyle(color = colors.inkMuted),
                match.groups[2]!!.range.first,
                match.groups[2]!!.range.last + 1,
            ),
        )
    }
    editorLinePrefix.findAll(source).forEach { match ->
        add(EditorStyleRange(SpanStyle(color = colors.accent), match.range.first, match.range.last + 1))
    }
}

internal fun editorHeadingFontSizeSp(baseFontSizeSp: Float, level: Int): Float =
    baseFontSizeSp * when (level) {
        1 -> 23f / 17f
        2 -> 21f / 17f
        else -> 19f / 17f
    }

fun markdownEditorOutputTransformation(
    highlights: () -> List<DisplayHighlight>,
    fontSizeSp: () -> Float,
    colors: TextoryColors = LightTextoryColors,
): OutputTransformation = OutputTransformation {
    val source = asCharSequence().toString()
    markdownEditorStyleRanges(source, fontSizeSp(), colors).forEach { range ->
        if (range.start < range.end) addStyle(range.style, range.start, range.end)
    }
    val highlightStyle = SpanStyle(
        background = colors.greenHighlight.copy(alpha = EDITOR_DIFF_HIGHLIGHT_ALPHA),
    )
    highlights().forEach { highlight ->
        val start = highlight.start.coerceIn(0, source.length)
        val end = highlight.end.coerceIn(start, source.length)
        if (start < end) addStyle(highlightStyle, start, end)
    }
}
