package mom.cosmism.textory.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

class PassiveDoubleTapTracker {
    var firstTapTime: Long = 0L
    var firstTapPosition: Offset? = null

    fun reset() {
        firstTapTime = 0L
        firstTapPosition = null
    }
}

fun Modifier.observeTapWithoutConsuming(
    vararg keys: Any?,
    onTap: (Offset) -> Unit,
): Modifier = pointerInput(*keys) {
    var activePointer: PointerId? = null
    var downTime = 0L
    var downPosition = Offset.Zero
    var moved = false
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (activePointer == null) {
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { down ->
                    activePointer = down.id
                    downTime = down.uptimeMillis
                    downPosition = down.position
                    moved = false
                }
                continue
            }

            val change = event.changes.firstOrNull { it.id == activePointer }
            if (change == null) {
                if (event.changes.none { it.pressed }) activePointer = null
                continue
            }
            if (change.pressed) {
                if (abs(change.position.x - downPosition.x) > viewConfiguration.touchSlop ||
                    abs(change.position.y - downPosition.y) > viewConfiguration.touchSlop
                ) {
                    moved = true
                }
                continue
            }
            if (!change.previousPressed) continue

            activePointer = null
            val pressDuration = change.uptimeMillis - downTime
            if (!moved && pressDuration < viewConfiguration.longPressTimeoutMillis) {
                onTap(change.position)
            }
        }
    }
}

fun Modifier.observeDoubleTapWithoutConsuming(
    tracker: PassiveDoubleTapTracker,
    vararg keys: Any?,
    onDoubleTap: (first: Offset, second: Offset) -> Unit,
): Modifier = pointerInput(*keys) {
    var activePointer: PointerId? = null
    var downTime = 0L
    var downPosition = Offset.Zero
    var moved = false
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (activePointer == null) {
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { down ->
                    activePointer = down.id
                    downTime = down.uptimeMillis
                    downPosition = down.position
                    moved = false
                }
                continue
            }

            val change = event.changes.firstOrNull { it.id == activePointer }
            if (change == null) {
                if (event.changes.none { it.pressed }) activePointer = null
                continue
            }

            if (change.pressed) {
                if (abs(change.position.x - downPosition.x) > viewConfiguration.touchSlop ||
                    abs(change.position.y - downPosition.y) > viewConfiguration.touchSlop
                ) {
                    moved = true
                }
                continue
            }

            if (!change.previousPressed) continue
            activePointer = null
            val pressDuration = change.uptimeMillis - downTime
            if (moved || pressDuration >= viewConfiguration.longPressTimeoutMillis) {
                tracker.reset()
                continue
            }

            val previousPosition = tracker.firstTapPosition
            val interval = change.uptimeMillis - tracker.firstTapTime
            val doubleTapRadius = maxOf(viewConfiguration.touchSlop * 4, 64.dp.toPx())
            val nearPrevious = previousPosition != null &&
                abs(change.position.x - previousPosition.x) <= doubleTapRadius &&
                abs(change.position.y - previousPosition.y) <= doubleTapRadius
            if (tracker.firstTapTime != 0L &&
                interval in DOUBLE_TAP_MIN_INTERVAL_MS..DOUBLE_TAP_MAX_INTERVAL_MS &&
                nearPrevious
            ) {
                tracker.reset()
                onDoubleTap(previousPosition, change.position)
            } else {
                tracker.firstTapTime = change.uptimeMillis
                tracker.firstTapPosition = change.position
            }
        }
    }
}

private const val DOUBLE_TAP_MIN_INTERVAL_MS = 25L
private const val DOUBLE_TAP_MAX_INTERVAL_MS = 550L
