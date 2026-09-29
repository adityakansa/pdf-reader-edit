package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * FR-041 / FR-043: long-press a page, drag it, drop it. The list is reordered live through `onMove`
 * each time the finger crosses into another item, so the saved order is always what is on screen.
 *
 * One state works for a grid and a row: the lazy container supplies [items] (visible items as
 * index + bounds in container coordinates) and [scrollBy] for edge auto-scroll.
 */
@Stable
class DragReorderState internal constructor(
    private val items: () -> List<Pair<Int, Rect>>,
    private val scrollBy: (Offset) -> Unit,
    private val edge: Float,
    private val viewport: () -> Rect,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onStart: () -> Unit,
) {
    /** Index of the item being dragged, or null. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    private var pointer by mutableStateOf(Offset.Zero)
    private var grab = Offset.Zero

    internal fun start(at: Offset) {
        val (index, bounds) = items().firstOrNull { it.second.contains(at) } ?: return
        draggingIndex = index
        pointer = at
        grab = at - bounds.topLeft
        onStart()
    }

    internal fun drag(delta: Offset) {
        val dragging = draggingIndex ?: return
        pointer += delta
        val target = items().firstOrNull { (index, bounds) -> index != dragging && bounds.contains(pointer) }
        if (target != null) {
            onMove(dragging, target.first)
            draggingIndex = target.first
        }
        val area = viewport()
        val scroll = Offset(
            x = when {
                pointer.x < area.left + edge -> -SCROLL_STEP
                pointer.x > area.right - edge -> SCROLL_STEP
                else -> 0f
            },
            y = when {
                pointer.y < area.top + edge -> -SCROLL_STEP
                pointer.y > area.bottom - edge -> SCROLL_STEP
                else -> 0f
            },
        )
        if (scroll != Offset.Zero) scrollBy(scroll)
    }

    internal fun end() {
        draggingIndex = null
    }

    /** How far the dragged item is drawn from its laid-out position, so it stays under the finger. */
    fun offsetOf(index: Int): Offset {
        if (index != draggingIndex) return Offset.Zero
        val bounds = items().firstOrNull { it.first == index }?.second ?: return Offset.Zero
        return pointer - grab - bounds.topLeft
    }

    private companion object {
        const val SCROLL_STEP = 12f
    }
}

@Composable
fun rememberGridReorderState(grid: LazyGridState, onMove: (from: Int, to: Int) -> Unit): DragReorderState {
    val move by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { EDGE.toPx() }
    return remember(grid) {
        DragReorderState(
            items = {
                val info = grid.layoutInfo
                // Item offsets are measured from the viewport start, which sits before any content padding.
                val shift = -info.viewportStartOffset.toFloat()
                info.visibleItemsInfo.map { item ->
                    item.index to Rect(
                        Offset(item.offset.x.toFloat(), item.offset.y + shift),
                        androidx.compose.ui.geometry.Size(item.size.width.toFloat(), item.size.height.toFloat()),
                    )
                }
            },
            scrollBy = { grid.dispatchRawDelta(it.y) },
            edge = edge,
            viewport = {
                val size = grid.layoutInfo.viewportSize
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            },
            onMove = { from, to -> move(from, to) },
            onStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }
}

@Composable
fun rememberRowReorderState(row: LazyListState, onMove: (from: Int, to: Int) -> Unit): DragReorderState {
    val move by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { EDGE.toPx() }
    return remember(row) {
        DragReorderState(
            items = {
                val info = row.layoutInfo
                val shift = -info.viewportStartOffset.toFloat()
                val horizontal = info.orientation == Orientation.Horizontal
                val crossSize = if (horizontal) info.viewportSize.height else info.viewportSize.width
                info.visibleItemsInfo.map { item ->
                    val start = item.offset + shift
                    item.index to if (horizontal) {
                        Rect(start, 0f, start + item.size, crossSize.toFloat())
                    } else {
                        Rect(0f, start, crossSize.toFloat(), start + item.size)
                    }
                }
            },
            scrollBy = {
                row.dispatchRawDelta(if (row.layoutInfo.orientation == Orientation.Horizontal) it.x else it.y)
            },
            edge = edge,
            viewport = {
                val size = row.layoutInfo.viewportSize
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            },
            onMove = { from, to -> move(from, to) },
            onStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }
}

/** Put on the lazy container: long-press starts a drag, releasing drops the item where it is. */
fun Modifier.dragReorder(state: DragReorderState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = state::start,
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount)
        },
        onDragEnd = state::end,
        onDragCancel = state::end,
    )
}

/** Put on each item: lifts and moves the one being dragged; everything else is untouched. */
fun Modifier.reorderItem(state: DragReorderState, index: Int): Modifier {
    val dragging = state.draggingIndex == index
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            val offset = state.offsetOf(index)
            translationX = offset.x
            translationY = offset.y
            if (dragging) {
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
                shadowElevation = LIFT_ELEVATION.toPx()
            }
        }
}

private val EDGE = 48.dp
private val LIFT_ELEVATION = 8.dp
private const val LIFT_SCALE = 1.05f
