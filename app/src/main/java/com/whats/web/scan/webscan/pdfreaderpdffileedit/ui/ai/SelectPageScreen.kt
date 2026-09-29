package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.NativeAdSlot
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.Disabled
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.SelectBlue

/** FR-062 (S01). Page thumbnails with the free/Pro limit, and the quit guard on back. */
@Composable
fun SelectPageScreen(
    key: String,
    job: AiJob,
    onBack: () -> Unit,
    onContinue: (List<Int>) -> Unit,
    onPaywall: () -> Unit,
    canShowAds: Boolean,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var quitOpen by remember { mutableStateOf(false) }

    LaunchedEffect(key, job) { viewModel.open(key, job) }
    LaunchedEffect(state.limitMessage) {
        state.limitMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    fun tryBack() {
        if (state.selectedPages.isEmpty()) onBack() else quitOpen = true
    }
    BackHandler { tryBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { tryBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                Text(
                    stringResource(R.string.select_page),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(Modifier.size(48.dp))
            }
        },
        bottomBar = {
            Column {
                Button(
                    onClick = { onContinue(state.selectedPages.sorted()) },
                    enabled = state.selectedPages.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SelectBlue,
                        disabledContainerColor = Disabled,
                    ),
                ) { Text(stringResource(R.string.action_select)) }
                NativeAdSlot(visible = canShowAds && !state.isPro)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.pages_count, state.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.choose_pages, state.pageLimit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items((0 until state.pageCount).toList()) { index ->
                    PageThumb(
                        index = index,
                        selected = index in state.selectedPages,
                        load = { viewModel.thumbnail(index) },
                        onClick = { viewModel.togglePage(index, onLimit = onPaywall) },
                    )
                }
            }
        }
    }

    if (quitOpen) {
        QuitDialog(
            job = job,
            onQuit = { quitOpen = false; viewModel.cancel(); onBack() },
            onDismiss = { quitOpen = false },
        )
    }
}

@Composable
private fun PageThumb(index: Int, selected: Boolean, load: suspend () -> Bitmap?, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, index) { value = load() }
    Box(
        Modifier
            .aspectRatio(0.72f)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) SelectBlue else MaterialTheme.colorScheme.outline,
            )
            .clickable(onClick = onClick),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) SelectBlue else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}
