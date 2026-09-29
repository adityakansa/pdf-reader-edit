package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.review

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.QuadEditing
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.NormalizedPoint
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageFilter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.ScanPage
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/** FR-043 / FR-044. Crop, rotate, filter, reorder and delete the captured pages, then write the PDF. */
@Composable
fun PageReviewScreen(
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PageReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var cropping by remember { mutableStateOf(false) }
    val page = state.pages.getOrNull(state.current)
    val undoLabel = stringResource(R.string.action_undo)
    val deletedLabel = stringResource(R.string.page_deleted)

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.undoable) {
        if (state.undoable == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedLabel, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.undoShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PageFilter.ORIGINAL to R.string.filter_original,
                        PageFilter.AUTO_ENHANCE to R.string.filter_enhanced,
                        PageFilter.GRAYSCALE to R.string.filter_grey,
                        PageFilter.BLACK_AND_WHITE to R.string.filter_bw,
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = page?.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = { cropping = !cropping }) {
                        Icon(
                            Icons.Filled.Crop,
                            contentDescription = null,
                            tint = if (cropping) BrandRed else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = viewModel::rotate) {
                        Icon(Icons.Filled.RotateRight, contentDescription = stringResource(R.string.action_rotate))
                    }
                    IconButton(onClick = viewModel::moveLater) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                    IconButton(onClick = viewModel::delete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                    TextButton(onClick = onAddPage) { Text("+") }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = { viewModel.discard(); onBack() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_discard)) }
                    Button(
                        onClick = viewModel::save,
                        enabled = state.pages.isNotEmpty() && !state.saving,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.saving -> CircularProgressIndicator()
                    page == null -> Text(stringResource(R.string.empty_documents))
                    cropping -> CropEditor(
                        page = page,
                        load = { viewModel.original(it, PREVIEW_MAX_SIDE) },
                        onCrop = viewModel::setCrop,
                    )

                    else -> PagePreview(page) { viewModel.preview(it, PREVIEW_MAX_SIDE) }
                }
            }
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(state.pages, key = { _, item -> item.id }) { index, item ->
                    Box(
                        Modifier
                            .size(60.dp, 80.dp)
                            .border(
                                width = if (index == state.current) 2.dp else 1.dp,
                                color = if (index == state.current) BrandRed else MaterialTheme.colorScheme.outline,
                            )
                            .clickable { viewModel.select(index) },
                    ) {
                        PagePreview(item) { viewModel.preview(it, THUMB_MAX_SIDE) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagePreview(page: ScanPage, load: suspend (ScanPage) -> Bitmap?) {
    val bitmap by produceState<Bitmap?>(null, page) { value = load(page) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** FR-043: drag a corner; the quad stays convex so a crop can never fold over itself. */
@Composable
private fun CropEditor(page: ScanPage, load: suspend (ScanPage) -> Bitmap?, onCrop: (Quad) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, page) { value = load(page) }
    var quad by remember(page.id) { mutableStateOf(page.crop) }
    var dragging by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize()) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(page.id) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val point = NormalizedPoint(
                                (offset.x / size.width).coerceIn(0f, 1f),
                                (offset.y / size.height).coerceIn(0f, 1f),
                            )
                            dragging = QuadEditing.nearestCorner(
                                quad,
                                point,
                                size.width.toFloat() / size.height,
                                CORNER_GRAB,
                            )
                        },
                        onDragEnd = {
                            dragging = null
                            onCrop(quad)
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        val corner = dragging ?: return@detectDragGestures
                        quad = QuadEditing.moveCorner(
                            quad = quad,
                            index = corner,
                            targetX = change.position.x / size.width,
                            targetY = change.position.y / size.height,
                            // The detected crop is what the corner snaps back to (FR-043).
                            detected = page.crop,
                            aspect = size.width.toFloat() / size.height,
                            snapDistance = CORNER_SNAP,
                        )
                    }
                }
                .drawBehind {
                    val path = Path().apply {
                        quad.points.forEachIndexed { index, point ->
                            val x = point.x * size.width
                            val y = point.y * size.height
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path, BrandRed, style = Stroke(width = 4f))
                    quad.points.forEach { point ->
                        drawCircle(
                            color = Color.White,
                            radius = 18f,
                            center = Offset(point.x * size.width, point.y * size.height),
                        )
                        drawCircle(
                            color = BrandRed,
                            radius = 18f,
                            center = Offset(point.x * size.width, point.y * size.height),
                            style = Stroke(width = 4f),
                        )
                    }
                },
        )
    }
}

private const val PREVIEW_MAX_SIDE = 1_600
private const val THUMB_MAX_SIDE = 240

/** How near a finger has to land, as a fraction of the image, to grab a corner. */
private const val CORNER_GRAB = 0.12f

/** How near the detected corner a drag has to end to snap onto it. */
private const val CORNER_SNAP = 0.03f
