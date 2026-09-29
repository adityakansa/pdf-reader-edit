package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MotionPhotosAuto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** FR-042. CameraX preview + analysis, the live page outline, auto-capture and gallery import. */
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onReview: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> viewModel.importPhotos(uris) }

    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    var confirmExit by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // A white blink on every shot, like the system camera, so a capture never goes unnoticed.
    val shutterFlash = remember { Animatable(0f) }

    fun leave() {
        if (state.pageCount > 0) confirmExit = true else onBack()
    }
    BackHandler { leave() }

    fun capture() {
        val capture = imageCapture ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shutterFlash.snapTo(0.7f)
            shutterFlash.animateTo(0f, tween(SHUTTER_FLASH_MILLIS))
        }
        viewModel.captureStarted()
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.use { it.toJpegBytes() }
                    viewModel.onCaptured(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.captureFailed()
                }
            },
        )
    }

    LaunchedEffect(state.fireCapture) { if (state.fireCapture) capture() }
    LaunchedEffect(state.flashOn, imageCapture) {
        imageCapture?.flashMode =
            if (state.flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!granted) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.camera_permission_body),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(stringResource(R.string.open_settings)) }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    androidx.camera.view.PreviewView(ctx).also { view ->
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build()
                                .also { it.surfaceProvider = view.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analyser ->
                                    analyser.setAnalyzer(executor) { image ->
                                        image.use { viewModel.onFrame(it, inFocus = true) }
                                    }
                                }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                                capture,
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
            )
            EdgeOutline(state.quad, Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = shutterFlash.value)),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { leave() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_camera),
                    tint = Color.White,
                )
            }
            Box(Modifier.weight(1f))
            // Labelled toggles: an icon alone ("timer"? "bolt"?) is what confuses people in scanner apps.
            CameraToggle(
                icon = if (state.autoCapture) Icons.Filled.MotionPhotosAuto else Icons.Filled.TouchApp,
                label = stringResource(if (state.autoCapture) R.string.capture_auto else R.string.capture_manual),
                active = state.autoCapture,
                onClick = viewModel::toggleAutoCapture,
            )
            CameraToggle(
                icon = if (state.flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                label = stringResource(if (state.flashOn) R.string.flash_on else R.string.flash_off),
                active = state.flashOn,
                onClick = viewModel::toggleFlash,
            )
        }

        if (granted) {
            Text(
                text = stringResource(
                    when {
                        state.capturing -> R.string.scan_hint_capturing
                        state.quad == null -> R.string.scan_hint_searching
                        state.autoCapture -> R.string.scan_hint_hold_steady
                        else -> R.string.scan_hint_tap
                    },
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) {
                        pickPhotos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
                Text(
                    stringResource(R.string.scan_import),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val shutterLabel = stringResource(R.string.cd_shutter)
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (state.capturing) BrandRed.copy(alpha = 0.5f) else BrandRed)
                    .clickable(enabled = !state.capturing, role = Role.Button) { capture() }
                    .semantics { contentDescription = shutterLabel },
                contentAlignment = Alignment.Center,
            ) {
                // Auto-capture fills the ring as the page holds still, so the wait is visible.
                if (state.autoCapture && state.autoCaptureProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { state.autoCaptureProgress },
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Button(
                onClick = onReview,
                enabled = state.pageCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    if (state.pageCount > 0) {
                        stringResource(R.string.scan_done_count, state.pageCount)
                    } else {
                        stringResource(R.string.action_done)
                    },
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.scan_discard_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.scan_discard_body, state.pageCount, state.pageCount),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; viewModel.discard(); onBack() }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; onReview() }) {
                    Text(stringResource(R.string.scan_keep_review))
                }
            },
        )
    }
}

@Composable
private fun CameraToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (active) CrownGold else Color.White)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

private const val SHUTTER_FLASH_MILLIS = 220

/** FR-042: the detected page drawn over the preview, in the preview's own coordinates. */
@Composable
private fun EdgeOutline(quad: Quad?, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val points = quad?.points ?: return@drawBehind
            val path = Path().apply {
                points.forEachIndexed { index, point ->
                    val offset = Offset(point.x * size.width, point.y * size.height)
                    if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                }
                close()
            }
            drawPath(path, BrandRed.copy(alpha = 0.18f))
            drawPath(path, BrandRed, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        },
    )
}

private fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** CameraX hands back a JPEG in a single plane; this is just the bytes out of it. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return if (format == android.graphics.ImageFormat.JPEG) {
        bytes
    } else {
        // Defensive: a non-JPEG capture would otherwise write an unreadable page file.
        ByteArrayOutputStream().use { out ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }
}
