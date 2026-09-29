package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfMarkup
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageDimensions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.HighlightYellow
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.PageGrey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * FR-030. Continuous vertical scroll; pinch zooms by rendering the page wider than the screen rather
 * than scaling a bitmap up, so text stays sharp. Ported from pdfscanner `feature/reader/.../PdfPages.kt`
 * with the paged and night modes dropped — this app only ships the continuous reader.
 */
@OptIn(FlowPreview::class)
@Composable
fun PdfPages(
    pages: List<PdfPageDimensions>,
    render: suspend (index: Int, widthPixels: Int) -> Bitmap?,
    onPageShown: (Int) -> Unit,
    highlights: List<PdfMarkup>,
    highlightMode: Boolean,
    onHighlight: (page: Int, left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    jumpTo: Int?,
    tool: MarkupKind = MarkupKind.HIGHLIGHT,
    penColor: Int = 0xFF000000.toInt(),
    onInk: (page: Int, points: List<Pair<Float, Float>>) -> Unit = { _, _ -> },
    onJumped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()
    var viewportPixels by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Re-rendering at a new width is expensive, so it waits for the pinch to settle.
    var settledZoom by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect(onPageShown)
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null && jumpTo in pages.indices) {
            listState.scrollToItem(jumpTo)
            onJumped()
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { zoom }.debounce(ZOOM_SETTLE_MS).collect { settledZoom = it }
    }

    val pageWidth = with(density) { viewportPixels.toDp() } * zoom
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(PageGrey)
            .onSizeChanged { viewportPixels = it.width }
            .then(if (highlightMode) Modifier else Modifier.zoomable(zoom) { zoom = it })
            .horizontalScroll(horizontal),
        verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
    ) {
        items(pages.size, key = { it }) { index ->
            Page(
                index = index,
                page = pages[index],
                widthPixels = (viewportPixels * settledZoom).toInt(),
                render = render,
                highlights = highlights.filter { it.page == index },
                highlightMode = highlightMode,
                onHighlight = { l, t, r, b -> onHighlight(index, l, t, r, b) },
                tool = tool,
                penColor = penColor,
                onInk = { points -> onInk(index, points) },
                modifier = Modifier.width(pageWidth),
            )
        }
    }
}

@Composable
private fun Page(
    index: Int,
    page: PdfPageDimensions,
    widthPixels: Int,
    render: suspend (Int, Int) -> Bitmap?,
    highlights: List<PdfMarkup>,
    highlightMode: Boolean,
    onHighlight: (Float, Float, Float, Float) -> Unit,
    tool: MarkupKind,
    penColor: Int,
    onInk: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    // The stroke being drawn with the pen, in pixels of this page.
    val stroke = remember { mutableStateListOf<Offset>() }

    Box(
        modifier = modifier
            .aspectRatio(page.aspectRatio)
            .background(Color.White),
    ) {
        if (widthPixels <= 0) return@Box
        val bitmap by produceState<Bitmap?>(initialValue = null, index, widthPixels) {
            value = render(index, widthPixels)
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (!highlightMode) {
                        Modifier
                    } else if (tool == MarkupKind.INK) {
                        Modifier.pointerInput(index, tool) {
                            detectDragGestures(
                                onDragStart = { stroke.clear(); stroke.add(it) },
                                onDragEnd = {
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    onInk(stroke.map { (it.x / w).coerceIn(0f, 1f) to (it.y / h).coerceIn(0f, 1f) })
                                    stroke.clear()
                                },
                                onDragCancel = { stroke.clear() },
                            ) { change, _ ->
                                change.consume()
                                stroke.add(change.position)
                            }
                        }
                    } else Modifier.pointerInput(index, tool) {
                        detectDragGestures(
                            onDragStart = { dragStart = it; dragEnd = it },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragEnd
                                if (start != null && end != null) {
                                    onHighlight(
                                        minOf(start.x, end.x) / size.width,
                                        minOf(start.y, end.y) / size.height,
                                        maxOf(start.x, end.x) / size.width,
                                        maxOf(start.y, end.y) / size.height,
                                    )
                                }
                                dragStart = null
                                dragEnd = null
                            },
                            onDragCancel = { dragStart = null; dragEnd = null },
                        ) { change, _ -> dragEnd = change.position }
                    },
                )
                .drawBehind {
                    highlights.forEach { mark -> drawMark(mark) }
                    if (stroke.size >= 2) {
                        drawPath(
                            Path().apply {
                                moveTo(stroke.first().x, stroke.first().y)
                                stroke.drop(1).forEach { lineTo(it.x, it.y) }
                            },
                            color = Color(penColor),
                            style = Stroke(width = PEN_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    val start = dragStart
                    val end = dragEnd
                    if (start != null && end != null) {
                        drawRect(
                            color = HighlightYellow.copy(alpha = 0.25f),
                            topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                            size = Size(
                                kotlin.math.abs(end.x - start.x),
                                kotlin.math.abs(end.y - start.y),
                            ),
                        )
                    }
                },
        )
    }
}

/** A pending mark as it will look once saved. */
private fun DrawScope.drawMark(mark: PdfMarkup) {
    val left = mark.left * size.width
    val right = mark.right * size.width
    val top = mark.top * size.height
    val bottom = mark.bottom * size.height
    val color = Color(mark.colorArgb)
    when (mark.kind) {
        MarkupKind.HIGHLIGHT -> drawRect(
            color = HighlightYellow.copy(alpha = 0.4f),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
        )
        MarkupKind.UNDERLINE -> drawLine(color, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2.dp.toPx())
        MarkupKind.STRIKEOUT -> {
            val middle = (top + bottom) / 2f
            drawLine(color, Offset(left, middle), Offset(right, middle), strokeWidth = 2.dp.toPx())
        }
        MarkupKind.INK -> mark.strokes.filter { it.size >= 2 }.forEach { points ->
            drawPath(
                Path().apply {
                    moveTo(points.first().first * size.width, points.first().second * size.height)
                    points.drop(1).forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
                },
                color = color,
                style = Stroke(width = PEN_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private val PEN_WIDTH = 2.5.dp

/** Pinch to zoom between 1× and 5×. One-finger drags are left alone so the list still scrolls. */
private fun Modifier.zoomable(zoom: Float, onZoom: (Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var current = zoom
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                current = (current * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                onZoom(current)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}.pointerInput(Unit) {
    detectTapGestures(onDoubleTap = { onZoom(if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM) })
}

private val PAGE_GAP = 8.dp
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOM_SETTLE_MS = 250L
