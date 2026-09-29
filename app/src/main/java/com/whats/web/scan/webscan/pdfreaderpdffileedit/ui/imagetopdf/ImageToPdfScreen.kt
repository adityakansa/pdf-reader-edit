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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageMargin
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPageSize

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
                    onClick = viewModel::save,
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.images, key = { it.toString() }) { uri ->
                    ImageTile(
                        uri = uri,
                        onRemove = { viewModel.remove(uri) },
                        onMoveLater = { viewModel.moveLater(uri) },
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

@Composable
private fun ImageTile(uri: Uri, onRemove: () -> Unit, onMoveLater: () -> Unit) {
    Box(
        Modifier
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
        IconButton(
            onClick = onMoveLater,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
