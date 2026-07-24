package mom.cosmism.textory.ui

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorScrollTest {
    @Test
    fun `keeps scroll when cursor already has breathing room`() {
        assertEquals(
            100,
            cursorScrollTarget(
                cursorTop = 200f,
                cursorBottom = 220f,
                currentScroll = 100,
                viewportHeight = 300,
                margin = 12f,
                maxScroll = 800,
            ),
        )
    }

    @Test
    fun `does not add a final shift when native scroll leaves cursor at lower edge`() {
        assertEquals(
            100,
            cursorScrollTarget(
                cursorTop = 380f,
                cursorBottom = 400f,
                currentScroll = 100,
                viewportHeight = 300,
                margin = 0f,
                maxScroll = 800,
            ),
        )
    }

    @Test
    fun `scrolls down only enough to reveal cursor`() {
        assertEquals(
            122,
            cursorScrollTarget(
                cursorTop = 390f,
                cursorBottom = 410f,
                currentScroll = 100,
                viewportHeight = 300,
                margin = 12f,
                maxScroll = 800,
            ),
        )
    }

    @Test
    fun `scrolls up only enough to reveal cursor`() {
        assertEquals(
            93,
            cursorScrollTarget(
                cursorTop = 105f,
                cursorBottom = 125f,
                currentScroll = 100,
                viewportHeight = 300,
                margin = 12f,
                maxScroll = 800,
            ),
        )
    }

    @Test
    fun `clamps target to scroll bounds`() {
        assertEquals(0, cursorScrollTarget(0f, 20f, 100, 300, 12f, 800))
        assertEquals(500, cursorScrollTarget(980f, 1_000f, 100, 300, 12f, 500))
    }

    @Test
    fun `recognizes rapid outbound values as echoes in order`() {
        val tracker = EditorStateEchoTracker()
        val first = EditorStateKey("a", TextRange(1))
        val second = EditorStateKey("ab", TextRange(2))
        tracker.recordOutbound(first)
        tracker.recordOutbound(second)

        assertTrue(tracker.consumeEcho(first))
        assertTrue(tracker.consumeEcho(second))
    }

    @Test
    fun `coalesced echo consumes superseded values`() {
        val tracker = EditorStateEchoTracker()
        val first = EditorStateKey("a", TextRange(1))
        val second = EditorStateKey("ab", TextRange(2))
        tracker.recordOutbound(first)
        tracker.recordOutbound(second)

        assertTrue(tracker.consumeEcho(second))
        assertFalse(tracker.consumeEcho(first))
    }

    @Test
    fun `does not mistake an external edit for an echo`() {
        val tracker = EditorStateEchoTracker()
        tracker.recordOutbound(EditorStateKey("draft", TextRange(5)))

        assertFalse(tracker.consumeEcho(EditorStateKey("**draft**", TextRange(9))))
    }
}
