package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.convert

import android.app.Activity
import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfCompressor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileTypeIcon
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.GradientButton
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.OutlineCtaButton
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Printing
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.Tool
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ScreenTitle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.TitleBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeExcel

/**
 * Step 12c (screens 56, 59, 60). One conversion from start to finish: the file, "Converting… 42%", then
 * "Converted successfully!" with rename, where it is saved, Share and Open, and a one-time "Are you
 * satisfied?" card.
 */
@Composable
fun ConvertScreen(
    key: String,
    tool: Tool,
    onBack: () -> Unit,
    onOpen: (android.net.Uri, String) -> Unit,
    viewModel: ConvertViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }

    LaunchedEffect(key, tool) { viewModel.load(key, tool) }
    BackHandler(enabled = state.phase == ConvertPhase.WORKING) { viewModel.cancel(); onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TitleBar(title = { ScreenTitle(stringResource(tool.label)) }, onBack = { viewModel.cancel(); onBack() }) },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            val result = state.result
            if (state.phase == ConvertPhase.DONE && result != null) {
                SuccessChip()
                ResultPreview(result, state.tool)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        result.first.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (result.outputs.size == 1) {
                        IconButton(onClick = { renaming = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                        }
                    }
                }
                if (result.outputs.size > 1) {
                    Text(
                        pluralStringResource(R.plurals.images_saved, result.outputs.size, result.outputs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.sizeBefore > 0 && result.sizeAfter > 0) {
                    val saved = 100 - (result.sizeAfter * 100 / result.sizeBefore)
                    Text(
                        stringResource(
                            R.string.compress_result,
                            Formatter.formatShortFileSize(context, result.sizeBefore),
                            Formatter.formatShortFileSize(context, result.sizeAfter),
                            saved.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TypeExcel,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button) { showLocation = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.view_locally),
                        textDecoration = TextDecoration.Underline,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                GradientButton(
                    stringResource(R.string.action_share),
                    onClick = { Intents.shareFiles(context, result.outputs.map { it.uri }, result.mimeType) },
                    modifier = Modifier.padding(top = 28.dp),
                )
                OutlineCtaButton(
                    stringResource(R.string.action_open),
                    onClick = {
                        if (result.mimeType.startsWith("image/")) Intents.openWith(context, result.first.uri, result.mimeType)
                        else onOpen(result.first.uri, result.mimeType)
                    },
                    modifier = Modifier.padding(top = 14.dp),
                )
                if (state.askRating) {
                    Spacer(Modifier.height(28.dp))
                    RatingCard(
                        feature = stringResource(state.tool.label),
                        onGood = {
                            viewModel.ratingAnswered()
                            (context as? Activity)?.let { activity ->
                                val manager = ReviewManagerFactory.create(activity)
                                manager.requestReviewFlow().addOnCompleteListener { task ->
                                    if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
                                }
                            }
                        },
                        onNotReally = {
                            viewModel.ratingAnswered()
                            Intents.email(
                                context,
                                BuildConfig.SUPPORT_EMAIL,
                                context.getString(R.string.mail_tool_feedback_subject, context.getString(state.tool.label)),
                            )
                        },
                        onClose = viewModel::ratingAnswered,
                    )
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            // Before and during the work: the file, then the options or the progress.
            Spacer(Modifier.height(32.dp))
            val file = state.file
            if (file != null) {
                FileTypeIcon(file.type, iconSize = 84.dp)
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
                if (file.size > 0) {
                    Text(
                        Formatter.formatShortFileSize(context, file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            when (state.phase) {
                ConvertPhase.READY -> if (state.tool == Tool.COMPRESS_PDF) {
                    CompressLevels(state.level, viewModel::setLevel)
                    GradientButton(stringResource(R.string.action_compress), onClick = viewModel::start, modifier = Modifier.padding(top = 20.dp))
                } else {
                    GradientButton(stringResource(R.string.action_convert), onClick = viewModel::start)
                }
                ConvertPhase.WORKING -> WorkingCard(state.progress, onCancel = { viewModel.cancel(); onBack() })
                ConvertPhase.FAILED -> FailedCard(
                    error = state.error,
                    onRetry = viewModel::start,
                    onPrintScreen = state.printHtml?.let { html ->
                        { Printing.printHtml(context, html, state.printBaseUrl, file?.name ?: "Document") }
                    },
                )
                ConvertPhase.DONE -> Unit
            }
        }
    }

    if (renaming) {
        val current = state.result?.first?.name.orEmpty()
        var text by remember { mutableStateOf(current.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    viewModel.rename(text) { ok ->
                        if (!ok) android.widget.Toast.makeText(context, R.string.rename_failed, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showLocation) {
        AlertDialog(
            onDismissRequest = { showLocation = false },
            title = { Text(stringResource(R.string.view_locally)) },
            text = { Text(stringResource(R.string.saved_location, state.result?.location.orEmpty())) },
            confirmButton = { TextButton(onClick = { showLocation = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun SuccessChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TypeExcel.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TypeExcel, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.converted_successfully),
            color = TypeExcel,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The first page picture for images, otherwise the file's type icon, on a small paper card. */
@Composable
private fun ResultPreview(result: ConvertResult, tool: Tool) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, result.first.uri) {
        if (result.mimeType.startsWith("image/")) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(result.first.uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        color = Color.White,
        modifier = Modifier
            .padding(top = 24.dp)
            .size(width = 120.dp, height = 156.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val shown = image
            if (shown != null) {
                Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                FileTypeIcon(if (tool == Tool.PDF_TO_WORD) DocType.WORD else DocType.PDF, iconSize = 64.dp)
            }
        }
    }
}

/** Screen 59: "Converting… 30%" with a red bar that fills smoothly. */
@Composable
private fun WorkingCard(progress: Float, onCancel: () -> Unit) {
    val shown by animateFloatAsState(progress, label = "progress")
    Surface(
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.converting),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(shown * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { shown },
                color = BrandRed,
                trackColor = BrandRed.copy(alpha = 0.12f),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Text(
                stringResource(R.string.converting_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun FailedCard(error: ConvertError?, onRetry: () -> Unit, onPrintScreen: (() -> Unit)?) {
    val message = when (error) {
        ConvertError.PASSWORD -> R.string.convert_error_password
        ConvertError.NOT_SMALLER -> R.string.convert_error_not_smaller
        ConvertError.PRINT_UNAVAILABLE -> R.string.convert_error_print
        else -> R.string.convert_error_failed
    }
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    when {
        error == ConvertError.PRINT_UNAVAILABLE && onPrintScreen != null ->
            GradientButton(stringResource(R.string.open_print_screen), onClick = onPrintScreen, modifier = Modifier.padding(top = 20.dp))
        error == ConvertError.FAILED ->
            GradientButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.padding(top = 20.dp))
        else -> Unit
    }
}

@Composable
private fun CompressLevels(level: PdfCompressor.Level, onPick: (PdfCompressor.Level) -> Unit) {
    val options = listOf(
        Triple(PdfCompressor.Level.LOW, R.string.compress_low, R.string.compress_low_body),
        Triple(PdfCompressor.Level.MEDIUM, R.string.compress_medium, R.string.compress_medium_body),
        Triple(PdfCompressor.Level.HIGH, R.string.compress_high, R.string.compress_high_body),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (value, title, body) ->
            val selected = value == level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(if (selected) 2.dp else 1.dp, if (selected) BrandRed else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .selectable(selected = selected, role = Role.RadioButton) { onPick(value) }
                    .padding(12.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Screen 60's card: "Are you satisfied with our "PDF to Word" feature?" 😐 Not really / 🥰 Good. */
@Composable
private fun RatingCard(feature: String, onGood: () -> Unit, onNotReally: () -> Unit, onClose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(
                    stringResource(R.string.rating_feature_question, feature),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                RatingChoice("😐  " + stringResource(R.string.rating_not_really), onNotReally, Modifier.weight(1f))
                RatingChoice("🥰  " + stringResource(R.string.rating_good), onGood, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RatingChoice(text: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
