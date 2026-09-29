package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.imagetopdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.SaveAsDialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageMargin
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPageSize
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.dragReorder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.reorderItem
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.rememberGridReorderState

/** FR-041. Photo Picker, so no media permission is ever requested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ImageToPdfViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ImageToPdfViewModel.MAX_IMAGES),
    ) { uris -> viewModel.add(uris) }

    LaunchedEffect(Unit) {
        if (state.images.isEmpty()) {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    var saveAsName by remember { mutableStateOf<String?>(null) }
    saveAsName?.let { suggested ->
        SaveAsDialog(
            suggested = suggested,
            onSave = { name -> saveAsName = null; viewModel.save(name) },
            onDismiss = { saveAsName = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_to_pdf)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                OptionRow(
                    label = stringResource(R.string.page_size),
                    options = listOf(
                        PdfPageSize.A4 to stringResource(R.string.page_size_a4),
                        PdfPageSize.LETTER to stringResource(R.string.page_size_letter),
                        PdfPageSize.FIT to stringResource(R.string.page_size_fit),
                    ),
                    selected = state.pageSize,
                    onSelect = viewModel::setPageSize,
                )
                OptionRow(
                    label = stringResource(R.string.margin),
                    options = listOf(
                        PageMargin.NONE to stringResource(R.string.margin_none),
                        PageMargin.SMALL to stringResource(R.string.margin_small),
                        PageMargin.WIDE to stringResource(R.string.margin_wide),
                    ),
                    selected = state.margin,
                    onSelect = viewModel::setMargin,
                )
                Button(
                    onClick = { saveAsName = viewModel.suggestedName() },
                    enabled = state.images.isNotEmpty() && !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            val gridState = rememberLazyGridState()
            val reorder = rememberGridReorderState(gridState, viewModel::move)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .dragReorder(reorder),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.images, key = { _, uri -> uri.toString() }) { index, uri ->
                    ImageTile(
                        uri = uri,
                        number = index + 1,
                        onRemove = { viewModel.remove(uri) },
                        modifier = Modifier.reorderItem(reorder, index),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> OptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

/** A small decode of a picked photo — enough for a 110 dp tile, and no image library in the APK. */
@Composable
private fun rememberThumbnail(uri: Uri): androidx.compose.ui.graphics.ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, options)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

/** One picked photo: its page number, and a remove button. Long-press anywhere on it to drag. */
@Composable
private fun ImageTile(uri: Uri, number: Int, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(0.75f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        rememberThumbnail(uri)?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
