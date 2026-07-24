package mom.cosmism.textory.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichClipboardHtmlTest {
    @Test
    fun `exports inline markdown styles and links as html`() {
        val builder = AnnotatedString.Builder("bold italic code site")
        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, 4)
        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), 5, 11)
        builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace), 12, 16)
        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), 17, 21)
        builder.addStringAnnotation(
            tag = RICH_TEXT_URL_TAG,
            annotation = "https://example.test/?a=1&b=2",
            start = 17,
            end = 21,
        )

        assertEquals(
            "<strong>bold</strong> <em>italic</em> <code>code</code> " +
                "<a href=\"https://example.test/?a=1&amp;b=2\">site</a>",
            selectedTextsToHtml(listOf(builder.toAnnotatedString())),
        )
    }

    @Test
    fun `preserves block emphasis and escapes selected text`() {
        val heading = AnnotatedString.Builder("Heading").apply {
            addStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), 0, 7)
        }.toAnnotatedString()

        assertEquals(
            "<span style=\"font-size:28.0pt\"><strong>Heading</strong></span><br>Body &amp; notes",
            selectedTextsToHtml(listOf(heading, AnnotatedString("Body & notes"))),
        )
    }

    @Test
    fun `resolves native plain selection back to rich document offsets`() {
        val document = AnnotatedString.Builder("before bold after").apply {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 7, 11)
        }.toAnnotatedString()

        assertEquals(
            "<strong>bold</strong>",
            selectedTextsToHtml(listOf(richSelectionForPlainText(document, "bold"))),
        )
    }

    @Test
    fun `falls back safely when selected text is not in rich document`() {
        assertEquals(
            "unknown &amp; safe",
            selectedTextsToHtml(
                listOf(richSelectionForPlainText(AnnotatedString("document"), "unknown & safe")),
            ),
        )
    }

    @Test
    fun `maps rendered markdown selection to rich html`() {
        val document = richClipboardDocumentForMarkdown(
            "# Heading\n\nText with **bold** and [site](https://example.test).\n\n`code`",
        )

        val boldHtml = selectedTextsToHtml(listOf(richSelectionForPlainText(document, "bold")))
        val linkHtml = selectedTextsToHtml(listOf(richSelectionForPlainText(document, "site")))
        val codeHtml = selectedTextsToHtml(listOf(richSelectionForPlainText(document, "code")))

        assertTrue(boldHtml.contains("<strong>bold</strong>"))
        assertTrue(boldHtml.contains("font-size:17.0pt"))
        assertTrue(linkHtml.contains("href=\"https://example.test\""))
        assertTrue(linkHtml.contains(">site</span></a>"))
        assertTrue(codeHtml.contains("<code>code</code>"))
    }

    @Test
    fun `inline code remains monospace without hiding native selection`() {
        val document = richClipboardDocumentForMarkdown("Text with `code` selected")
        val start = document.text.indexOf("code")
        val styles = document.spanStyles.filter { it.start <= start && it.end >= start + 4 }

        assertTrue(styles.any { it.item.fontFamily == FontFamily.Monospace })
        assertTrue(styles.none { it.item.background != Color.Unspecified })
    }
}
