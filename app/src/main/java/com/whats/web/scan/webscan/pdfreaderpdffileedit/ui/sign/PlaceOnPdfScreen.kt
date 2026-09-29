package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.sign.Placement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** FR-050 … FR-053 (Pro). Place saved signatures and text stamps on a page, then write a signed copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceOnPdfScreen(
    key: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PlaceOnPdfViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var padOpen by remember { mutableStateOf(false) }
    var stampOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var deleteSignature by remember { mutableStateOf<File?>(null) }

    fun leave() {
        if (state.placements.isNotEmpty() && !state.saved) confirmLeave = true else onBack()
    }
    BackHandler { leave() }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::importSignature) }

    LaunchedEffect(key) { viewModel.load(key) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.signatureLimitReached, state.noInkFound) {
        val message = when {
            state.signatureLimitReached -> context.getString(R.string.signature_limit)
            state.noInkFound -> context.getString(R.string.signature_none_found)
            else -> null
        } ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.messageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_edit_sign)) },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (state.selectedId != null) {
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.signatures, key = { it.absolutePath }) { file ->
                        Box(
                            Modifier
                                .size(88.dp, 44.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .clickable { viewModel.addSignature(file) },
                        ) {
                            SignatureThumb(file)
                            IconButton(
                                onClick = { deleteSignature = file },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.signature_delete),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SignTool(Icons.Filled.Draw, stringResource(R.string.signature_new)) { padOpen = true }
                    SignTool(Icons.Filled.ImageIcon, stringResource(R.string.signature_import)) {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    SignTool(Icons.Filled.TextFields, stringResource(R.string.add_text)) { stampOpen = true }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = state.placements.isNotEmpty() && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_save)) }
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
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.saving -> CircularProgressIndicator()
                    state.failed -> Text(stringResource(R.string.reader_open_failed))
                    state.pages.isEmpty() -> CircularProgressIndicator()
                    else -> PageCanvas(state, viewModel)
                }
                if (!state.saving && state.pages.isNotEmpty() && state.placements.none { it.page == state.currentPage }) {
                    Text(
                        stringResource(
                            if (state.signatures.isEmpty()) R.string.sign_hint_create else R.string.sign_hint_place,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(state.pages) { index, _ ->
                    Box(
                        Modifier
                            .size(36.dp, 44.dp)
                            .border(
                                if (index == state.currentPage) 2.dp else 1.dp,
                                if (index == state.currentPage) BrandRed else MaterialTheme.colorScheme.outline,
                            )
                            .clickable { viewModel.selectPage(index) },
                        contentAlignment = Alignment.Center,
                    ) { Text("${index + 1}", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }

    if (padOpen) {
        SignaturePadDialog(
            onSave = { bitmap: Bitmap ->
                viewModel.saveSignature(bitmap)
                padOpen = false
            },
            onDismiss = { padOpen = false },
        )
    }
    if (stampOpen) {
        StampDialog(
            onConfirm = { text -> viewModel.addText(text); stampOpen = false },
            onDismiss = { stampOpen = false },
        )
    }
    deleteSignature?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteSignature = null },
            title = { Text(stringResource(R.string.signature_delete_title)) },
            text = { Text(stringResource(R.string.signature_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSignature(file); deleteSignature = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSignature = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.sign_leave_title)) },
            text = { Text(stringResource(R.string.sign_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.sign_keep_editing)) }
            },
        )
    }
}

/** A labelled tool, so "draw a signature" is never a guess from an icon. */
@Composable
private fun SignTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PageCanvas(state: PlaceOnPdfUiState, viewModel: PlaceOnPdfViewModel) {
    val page = state.pages.getOrNull(state.currentPage) ?: return
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(page.aspectRatio),
    ) {
        val widthPixels = with(density) { maxWidth.roundToPx() }
        val bitmap by produceState<Bitmap?>(null, state.currentPage, widthPixels) {
            value = viewModel.render(state.currentPage, widthPixels)
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        state.placements.filter { it.page == state.currentPage }.forEach { placement ->
            PlacementBox(
                placement = placement,
                selected = placement.id == state.selectedId,
                boxWidth = maxWidth,
                boxHeight = maxHeight,
                onSelect = { viewModel.select(placement.id) },
                onGesture = { dx, dy, zoom, rotation ->
                    viewModel.gesture(placement.id, dx, dy, zoom, rotation)
                },
            )
        }
    }
}

@Composable
private fun PlacementBox(
    placement: Placement,
    selected: Boolean,
    boxWidth: androidx.compose.ui.unit.Dp,
    boxHeight: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onGesture: (Float, Float, Float, Float) -> Unit,
) {
    Box(
        Modifier
            .offset(x = boxWidth * placement.x, y = boxHeight * placement.y)
            .size(boxWidth * placement.width, boxHeight * placement.height)
            .rotate(placement.rotation)
            .then(if (selected) Modifier.border(1.dp, BrandRed) else Modifier)
            .clickable(onClick = onSelect)
            .pointerInput(placement.id, boxWidth, boxHeight) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onGesture(pan.x / size.width * placement.width, pan.y / size.height * placement.height, zoom, rotation)
                }
            },
    ) {
        if (placement.signature != null) {
            SignatureThumb(placement.signature)
        } else {
            Text(
                placement.text.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SignatureThumb(file: File) {
    val bitmap by produceState<Bitmap?>(null, file.absolutePath) {
        value = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** FR-052: a typed stamp, with today's date one tap away. */
@Composable
fun StampDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_text)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                TextButton(
                    onClick = {
                        text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    },
                ) { Text(stringResource(R.string.add_date)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
