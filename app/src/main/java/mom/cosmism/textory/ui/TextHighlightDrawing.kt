package mom.cosmism.textory.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

data class DisplayHighlight(
    val start: Int,
    val end: Int,
)

fun DrawScope.drawRoundedTextHighlights(
    layout: TextLayoutResult?,
    ranges: List<DisplayHighlight>,
    color: Color,
) {
    if (layout == null || layout.layoutInput.text.isEmpty()) return
    val horizontalInset = 2.dp.toPx()
    val verticalInset = 1.dp.toPx()
    val radius = 4.dp.toPx()
    val textLength = layout.layoutInput.text.length

    ranges.forEach { range ->
        textHighlightRects(layout, range, horizontalInset, verticalInset).forEach { rect ->
            drawRoundRect(
                color = color,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width.coerceAtLeast(1f), rect.height.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

internal fun textHighlightRects(
    layout: TextLayoutResult,
    range: DisplayHighlight,
    horizontalInset: Float,
    verticalInset: Float,
): List<Rect> {
    val textLength = layout.layoutInput.text.length
    val start = range.start.coerceIn(0, textLength)
    val end = range.end.coerceIn(start, textLength)
    if (start == end) return emptyList()

    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))
    return buildList {
        for (line in firstLine..lastLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line, visibleEnd = true)
            val segmentStart = max(start, lineStart)
            val segmentEnd = min(end, lineEnd)
            if (segmentStart >= segmentEnd) continue

            val left = if (segmentStart == lineStart) {
                layout.getLineLeft(line)
            } else {
                layout.getHorizontalPosition(segmentStart, usePrimaryDirection = true)
            }
            val right = if (segmentEnd >= lineEnd) {
                layout.getLineRight(line)
            } else {
                layout.getHorizontalPosition(segmentEnd, usePrimaryDirection = true)
            }
            add(
                Rect(
                    left = min(left, right) - horizontalInset,
                    top = layout.getLineTop(line) + verticalInset,
                    right = max(left, right) + horizontalInset,
                    bottom = layout.getLineBottom(line) - verticalInset,
                ),
            )
        }
    }
}
