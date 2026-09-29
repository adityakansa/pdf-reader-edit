package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.pdfeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfEdit
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageDimensions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.EditFont
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextLine
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.PasswordDialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CtaOrange
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CtaRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.PageGrey
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.SelectBlue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

/**
 * Step 12b (screens 61–66). The PDF editor: Edit text, Add text, Add image, and the way into Annotate and
 * Fill & Sign. Nothing is written until Save, and then into a copy.
 */
@Composable
fun PdfEditorScreen(
    key: String,
    initialTool: EditorTool? = null,
    onBack: () -> Unit,
    onSaved: (SavedPdf) -> Unit,
    onAnnotate: (String) -> Unit,
    onFillSign: (String) -> Unit,
    viewModel: PdfEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var saveFirst by remember { mutableStateOf<AfterSave?>(null) }
    var tips by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current

    var toolApplied by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(key) {
        viewModel.load(key)
        if (initialTool != null && !toolApplied) {
            toolApplied = true
            viewModel.selectTool(initialTool)
        }
    }
    LaunchedEffect(state.saved) { state.saved?.let { onSaved(it); viewModel.savedHandled() } }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addImage(uri, currentPage)
    }

    fun leave() {
        if (state.dirty) confirmLeave = true else onBack()
    }

    fun goTo(after: AfterSave) {
        if (state.dirty) {
            saveFirst = after
        } else if (after == AfterSave.ANNOTATE) {
            onAnnotate(key)
        } else {
            onFillSign(key)
        }
    }

    BackHandler {
        when {
            state.selectedId != null -> { focus.clearFocus(); viewModel.select(null) }
            else -> leave()
        }
    }

    Scaffold(
        containerColor = PageGrey,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                    IconButton(onClick = { tips = true }) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = stringResource(R.string.editor_tips))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo))
                    }
                    Button(
                        onClick = { focus.clearFocus(); viewModel.save() },
                        enabled = state.dirty && !state.saving,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
                }
            }
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                val selected = state.selected
                when {
                    selected is PdfEdit.Text -> FormatBar(
                        edit = selected,
                        onDone = { focus.clearFocus(); viewModel.select(null) },
                        onBigger = { viewModel.changeSize(1f) },
                        onSmaller = { viewModel.changeSize(-1f) },
                        onColor = viewModel::setColor,
                        onFont = viewModel::cycleFont,
                        onBold = viewModel::toggleBold,
                        onItalic = viewModel::toggleItalic,
                        onDelete = viewModel::deleteSelected,
                    )
                    selected is PdfEdit.Image -> ImageBar(
                        onDone = { viewModel.select(null) },
                        onDelete = viewModel::deleteSelected,
                    )
                    else -> {
                        ToolHint(state)
                        ToolBar(
                            tool = state.tool,
                            onTool = { tool ->
                                focus.clearFocus()
                                viewModel.selectTool(tool)
                                if (tool == EditorTool.ADD_IMAGE) {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            onAnnotate = { goTo(AfterSave.ANNOTATE) },
                            onFillSign = { goTo(AfterSave.FILL_SIGN) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BrandRed)
                state.failed -> Text(
                    stringResource(R.string.reader_open_failed),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                state.pages.isNotEmpty() -> EditorPages(
                    state = state,
                    viewModel = viewModel,
                    onPageShown = { currentPage = it },
                )
            }
        }
    }

    if (state.showIntro) EditorIntro(onTry = viewModel::dismissIntro)
    if (state.passwordRequired) {
        PasswordDialog(
            title = stringResource(R.string.reader_password_title),
            message = stringResource(R.string.reader_password_body),
            confirm = false,
            confirmLabel = stringResource(R.string.action_open),
            error = if (state.wrongPassword) stringResource(R.string.reader_password_wrong) else null,
            onConfirm = viewModel::submitPassword,
            onDismiss = onBack,
        )
    }
    if (state.saving) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.editor_saving), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
    if (state.saveFailed) {
        AlertDialog(
            onDismissRequest = viewModel::savedHandled,
            title = { Text(stringResource(R.string.editor_save_failed_title)) },
            text = { Text(stringResource(R.string.editor_save_failed_body)) },
            confirmButton = { TextButton(onClick = viewModel::savedHandled) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.pdfedit_leave_title)) },
            text = { Text(stringResource(R.string.pdfedit_leave_body)) },
            confirmButton = {
                Button(
                    onClick = { confirmLeave = false; viewModel.save() },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.action_discard)) }
            },
        )
    }
    saveFirst?.let { after ->
        AlertDialog(
            onDismissRequest = { saveFirst = null },
            title = { Text(stringResource(R.string.editor_save_first_title)) },
            text = { Text(stringResource(R.string.editor_save_first_body)) },
            confirmButton = {
                Button(
                    onClick = { saveFirst = null; viewModel.save(after) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                ) { Text(stringResource(R.string.editor_save_continue)) }
            },
            dismissButton = { TextButton(onClick = { saveFirst = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (tips) {
        AlertDialog(
            onDismissRequest = { tips = false },
            title = { Text(stringResource(R.string.editor_tips)) },
            text = { Text(stringResource(R.string.editor_tips_body)) },
            confirmButton = { TextButton(onClick = { tips = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** The pages, zoomable with two fingers; each page carries its edits and, in Edit text, the tappable lines. */
@OptIn(FlowPreview::class)
@Composable
private fun EditorPages(state: PdfEditorState, viewModel: PdfEditorViewModel, onPageShown: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var settledZoom by remember { mutableFloatStateOf(1f) }
    var showZoom by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.collect(onPageShown) }
    LaunchedEffect(Unit) {
        snapshotFlow { zoom }.debounce(ZOOM_SETTLE_MS).collect { settledZoom = it }
    }
    LaunchedEffect(zoom) {
        showZoom = true
        delay(ZOOM_LABEL_MS)
        showZoom = false
    }

    Box(Modifier.fillMaxSize()) {
        val pageWidth = with(density) { viewport.toDp() } * zoom
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it.width }
                .pinchZoom(zoom) { zoom = it }
                .horizontalScroll(horizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.pages.size, key = { it }) { index ->
                EditorPage(
                    index = index,
                    page = state.pages[index],
                    renderWidth = (viewport * settledZoom).toInt(),
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.width(pageWidth),
                )
            }
        }
        AnimatedVisibility(
            visible = showZoom && zoom != 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                "${(zoom * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun EditorPage(
    index: Int,
    page: PdfPageDimensions,
    renderWidth: Int,
    state: PdfEditorState,
    viewModel: PdfEditorViewModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(index, state.tool) { if (state.tool == EditorTool.EDIT_TEXT) viewModel.ensureLines(index) }
    BoxWithConstraints(
        modifier
            .aspectRatio(page.aspectRatio)
            .background(Color.White),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (renderWidth > 0) {
            val bitmap by produceState<Bitmap?>(null, index, renderWidth) { value = viewModel.render(index, renderWidth) }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val edits = state.edits.filter { it.page == index }
        val lines = if (state.tool == EditorTool.EDIT_TEXT) state.lines[index].orEmpty() else emptyList()
        val covered = edits.mapNotNull { (it as? PdfEdit.Text)?.cover }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(index, w, h) {
                    detectTapGestures { tap -> viewModel.tapPage(index, tap.x / w, tap.y / h) }
                }
                .drawBehind { drawLineHints(lines, covered) },
        )
        edits.forEach { edit ->
            EditView(
                edit = edit,
                selected = edit.id == state.selectedId,
                pageWidthPx = w,
                pageHeightPx = h,
                pxPerPoint = w / page.widthPoints,
                viewModel = viewModel,
            )
        }
    }
}

/** The dashed boxes around every line Edit text can change, as One Read shows them. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineHints(lines: List<TextLine>, covered: List<com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.EditBox>) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    lines.forEach { line ->
        if (covered.any { it.left == line.left && it.top == line.top }) return@forEach
        val pad = 2.dp.toPx()
        drawRect(
            color = SelectBlue.copy(alpha = 0.55f),
            topLeft = Offset(line.left * size.width - pad, line.top * size.height - pad),
            size = Size((line.right - line.left) * size.width + 2 * pad, (line.bottom - line.top) * size.height + 2 * pad),
            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
        )
    }
}

@Composable
private fun EditView(
    edit: PdfEdit,
    selected: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
    pxPerPoint: Float,
    viewModel: PdfEditorViewModel,
) {
    val density = LocalDensity.current
    val box = edit.box
    val x = box.left * pageWidthPx
    val y = box.top * pageHeightPx
    val width = with(density) { (box.width * pageWidthPx).toDp() }
    val height = with(density) { (box.height * pageHeightPx).toDp() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The white patch hides the old words on screen; in the saved file they are removed instead.
    if (edit is PdfEdit.Text) {
        edit.cover?.let { cover ->
            if (!edit.unchanged) {
                Box(
                    Modifier
                        .offset { IntOffset((cover.left * pageWidthPx).toInt() - 2, (cover.top * pageHeightPx).toInt() - 2) }
                        .size(
                            with(density) { (cover.width * pageWidthPx + 4).toDp() },
                            with(density) { (cover.height * pageHeightPx * 1.25f + 4).toDp() },
                        )
                        .background(Color.White),
                )
            }
        }
    }
    Box(
        Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .size(width, height)
            .then(if (selected) Modifier.border(1.5.dp, BrandRed) else Modifier),
    ) {
        when (edit) {
            is PdfEdit.Text -> {
                val style = textStyle(edit, pxPerPoint, density)
                if (selected) {
                    BasicTextField(
                        value = edit.text,
                        onValueChange = { viewModel.updateText(edit.id, it) },
                        textStyle = style,
                        cursorBrush = SolidColor(BrandRed),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focus),
                    )
                    LaunchedEffect(edit.id) {
                        runCatching { focus.requestFocus() }
                        keyboard?.show()
                    }
                } else {
                    Text(edit.text, style = style, modifier = Modifier.fillMaxSize())
                }
            }
            is PdfEdit.Image -> {
                val image = remember(edit.id) {
                    BitmapFactory.decodeByteArray(edit.bytes, 0, edit.bytes.size)?.asImageBitmap()
                }
                image?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
    if (selected) {
        // Move: the handle on the top-left corner. Resize: the one on the bottom-right.
        Handle(
            icon = Icons.Filled.OpenWith,
            description = stringResource(R.string.editor_move),
            modifier = Modifier.offset { IntOffset(x.toInt() - HANDLE_PX / 2, y.toInt() - HANDLE_PX - 4) },
            onStart = viewModel::beginGesture,
            onDrag = { dx, dy -> viewModel.move(edit.id, dx / pageWidthPx, dy / pageHeightPx) },
        )
        Handle(
            icon = null,
            description = stringResource(R.string.editor_resize),
            modifier = Modifier.offset {
                IntOffset(
                    (x + box.width * pageWidthPx).toInt() - HANDLE_PX / 2,
                    (y + box.height * pageHeightPx).toInt() - HANDLE_PX / 2,
                )
            },
            onStart = viewModel::beginGesture,
            onDrag = { dx, dy -> viewModel.resize(edit.id, dx / pageWidthPx, dy / pageHeightPx) },
        )
    }
}

@Composable
private fun Handle(
    icon: ImageVector?,
    description: String,
    modifier: Modifier,
    onStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier
            .size(with(density) { HANDLE_PX.toDp() })
            .clip(CircleShape)
            .background(if (icon == null) Color.White else BrandRed)
            .border(2.dp, BrandRed, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { onStart() }) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** The on-screen type matches what the saved PDF will draw: size in points, 1.2 line spacing, same family. */
private fun textStyle(edit: PdfEdit.Text, pxPerPoint: Float, density: androidx.compose.ui.unit.Density): TextStyle {
    val px = edit.fontSize * pxPerPoint
    return with(density) {
        TextStyle(
            color = Color(edit.colorArgb),
            fontSize = px.toSp(),
            lineHeight = (px * 1.2f).toSp(),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFamily = when (edit.font) {
                EditFont.SANS -> FontFamily.SansSerif
                EditFont.SERIF -> FontFamily.Serif
                EditFont.MONO -> FontFamily.Monospace
            },
            fontWeight = if (edit.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (edit.italic) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

@Composable
private fun ToolHint(state: PdfEditorState) {
    val text = when {
        state.noTextHint -> R.string.editor_hint_no_text
        state.tool == EditorTool.EDIT_TEXT -> R.string.editor_hint_edit
        state.tool == EditorTool.ADD_TEXT -> R.string.editor_hint_add_text
        else -> R.string.editor_hint_add_image
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Edit text · Add text · Add image · Annotate · Fill & Sign — screen 62's bottom bar. */
@Composable
private fun ToolBar(
    tool: EditorTool,
    onTool: (EditorTool) -> Unit,
    onAnnotate: () -> Unit,
    onFillSign: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            BarButton(Icons.Filled.FormatShapes, stringResource(R.string.tool_edit_text), tool == EditorTool.EDIT_TEXT) {
                onTool(EditorTool.EDIT_TEXT)
            }
            BarButton(Icons.Filled.TextFields, stringResource(R.string.tool_add_text), tool == EditorTool.ADD_TEXT) {
                onTool(EditorTool.ADD_TEXT)
            }
            BarButton(Icons.Filled.AddPhotoAlternate, stringResource(R.string.editor_add_image), tool == EditorTool.ADD_IMAGE) {
                onTool(EditorTool.ADD_IMAGE)
            }
            BarButton(Icons.Filled.BorderColor, stringResource(R.string.tool_annotate), false, onClick = onAnnotate)
            BarButton(Icons.Filled.Draw, stringResource(R.string.tool_fill_sign), false, onClick = onFillSign)
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) BrandRed else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandRed.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** Screen 63's formatting row: size up/down, colour, font, bold, italic, delete, done. */
@Composable
private fun FormatBar(
    edit: PdfEdit.Text,
    onDone: () -> Unit,
    onBigger: () -> Unit,
    onSmaller: () -> Unit,
    onColor: (Int) -> Unit,
    onFont: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onDelete: () -> Unit,
) {
    var colors by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done), tint = BrandRed)
            }
            IconButton(onClick = onBigger) {
                Icon(Icons.Filled.TextIncrease, contentDescription = stringResource(R.string.editor_bigger))
            }
            Text(
                "${edit.fontSize.toInt()}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onSmaller) {
                Icon(Icons.Filled.TextDecrease, contentDescription = stringResource(R.string.editor_smaller))
            }
            Box {
                IconButton(onClick = { colors = true }) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(edit.colorArgb))
                            .border(1.dp, Color.LightGray, CircleShape),
                    )
                }
                DropdownMenu(expanded = colors, onDismissRequest = { colors = false }) {
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        PdfEditorViewModel.COLORS.forEach { argb ->
                            Box(
                                Modifier
                                    .padding(4.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        if (argb == edit.colorArgb) 3.dp else 1.dp,
                                        if (argb == edit.colorArgb) BrandRed else Color.LightGray,
                                        CircleShape,
                                    )
                                    .clickable(role = Role.RadioButton) { onColor(argb); colors = false },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onFont) {
                Text(
                    "Aa",
                    fontFamily = when (edit.font) {
                        EditFont.SANS -> FontFamily.SansSerif
                        EditFont.SERIF -> FontFamily.Serif
                        EditFont.MONO -> FontFamily.Monospace
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onBold) {
                Icon(Icons.Filled.FormatBold, contentDescription = stringResource(R.string.editor_bold), tint = if (edit.bold) BrandRed else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onItalic) {
                Icon(Icons.Filled.FormatItalic, contentDescription = stringResource(R.string.editor_italic), tint = if (edit.italic) BrandRed else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun ImageBar(onDone: () -> Unit, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(stringResource(R.string.action_delete), modifier = Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onDone) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = BrandRed)
                Text(stringResource(R.string.action_done), color = BrandRed, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * Screen 61: the first time the editor opens, one page says what it does — a mock page with the dashed
 * text box and the editor's bar — then "Try now" or "Skip".
 */
@Composable
private fun EditorIntro(onTry: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onTry,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Spacer(Modifier.weight(0.4f))
                IntroMock()
                Text(
                    stringResource(R.string.editor_intro_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 28.dp),
                )
                Text(
                    stringResource(R.string.editor_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.weight(0.6f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(CtaOrange, CtaRed)))
                        .clickable(role = Role.Button, onClick = onTry),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.editor_try_now), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onTry, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.action_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IntroMock() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.width(250.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.menu_edit_pdf),
                    color = BrandRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(BrandRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(stringResource(R.string.tool_fill_sign), color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            }
            repeat(2) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(if (it == 0) 0.9f else 0.7f)
                        .height(8.dp)
                        .background(Color(0xFFE6E6E6), RoundedCornerShape(4.dp)),
                )
            }
            Text(
                stringResource(R.string.editor_intro_sample),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .border(1.dp, SelectBlue, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
            repeat(3) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(if (it == 1) 0.6f else 0.95f)
                        .height(8.dp)
                        .background(Color(0xFFE6E6E6), RoundedCornerShape(4.dp)),
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                listOf(Icons.Filled.FormatShapes, Icons.Filled.TextFields, Icons.Filled.AddPhotoAlternate, Icons.Filled.BorderColor, Icons.Filled.Draw)
                    .forEach { Icon(it, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

/** Two fingers zoom between 1× and 4×; one finger is left to scroll, tap and type. */
private fun Modifier.pinchZoom(zoom: Float, onZoom: (Float) -> Unit) = pointerInput(Unit) {
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
}

private const val HANDLE_PX = 56
private const val MAX_ZOOM = 4f
private const val ZOOM_SETTLE_MS = 250L
private const val ZOOM_LABEL_MS = 900L
