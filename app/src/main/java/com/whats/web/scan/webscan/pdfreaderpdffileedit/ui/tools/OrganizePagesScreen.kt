package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfToolRunner.PageAction
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.SaveAsDialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/**
 * Organize pages: tap pages to select them (numbers show the order), then Extract, Rotate or Delete.
 * Each action saves a new PDF and opens it; the original is never changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizePagesScreen(
    key: String,
    onBack: () -> Unit,
    viewModel: OrganizePagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<PageAction, String>?>(null) }

    LaunchedEffect(key) { viewModel.load(key) }
    LaunchedEffect(state.savedName, state.error) {
        val message = when {
            state.savedName != null -> context.getString(R.string.saved_to, state.savedName)
            state.error -> context.getString(R.string.tool_failed)
            else -> null
        } ?: return@LaunchedEffect
        viewModel.messageShown()
        snackbar.showSnackbar(message)
    }

    fun ask(action: PageAction) {
        if (action == PageAction.DELETE && state.selected.size >= state.pageCount) {
            pending = null
            return
        }
        pending = action to viewModel.suggestedName(action)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.organize_pages))
                        state.file?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (state.pageCount > 0) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.cd_select_all))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.pageCount > 0) {
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(
                            if (state.selected.isEmpty()) {
                                stringResource(R.string.organize_hint)
                            } else {
                                stringResource(R.string.selected_count, state.selected.size)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val enabled = state.selected.isNotEmpty() && !state.working
                            PageTool(Icons.Filled.ContentCopy, stringResource(R.string.pages_extract), enabled) {
                                ask(PageAction.EXTRACT)
                            }
                            PageTool(Icons.AutoMirrored.Filled.RotateLeft, stringResource(R.string.pages_rotate_left), enabled) {
                                ask(PageAction.ROTATE_LEFT)
                            }
                            PageTool(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.pages_rotate_right), enabled) {
                                ask(PageAction.ROTATE_RIGHT)
                            }
                            PageTool(
                                Icons.Filled.Delete,
                                stringResource(R.string.action_delete),
                                enabled && state.selected.size < state.pageCount,
                            ) { ask(PageAction.DELETE) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading || state.working -> CircularProgressIndicator(color = BrandRed)
                state.locked -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(40.dp))
                    Text(
                        stringResource(R.string.organize_locked),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                state.failed -> Text(stringResource(R.string.reader_open_failed))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items((0 until state.pageCount).toList(), key = { it }) { index ->
                        PageTile(
                            index = index,
                            order = state.selected.indexOf(index),
                            load = viewModel::thumbnail,
                            onClick = { viewModel.toggle(index) },
                        )
                    }
                }
            }
        }
    }

    pending?.let { (action, suggested) ->
        SaveAsDialog(
            suggested = suggested,
            onSave = { name -> pending = null; viewModel.run(action, name) },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun PageTile(index: Int, order: Int, load: suspend (Int) -> Bitmap?, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, index) { value = load(index) }
    val selected = order >= 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) BrandRed else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                )
                .clickable(role = Role.Checkbox, onClick = onClick),
        ) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Text(
                    "${order + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(BrandRed, CircleShape)
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            stringResource(R.string.page_number, index + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PageTool(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
