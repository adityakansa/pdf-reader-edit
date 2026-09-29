package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Timer
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

    fun capture() {
        val capture = imageCapture ?: return
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

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.discard(); onBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                )
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = viewModel::toggleAutoCapture) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = if (state.autoCapture) BrandRed else Color.White,
                )
            }
            IconButton(onClick = viewModel::toggleFlash) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = if (state.flashOn) BrandRed else Color.White,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    pickPhotos.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
            }
            Box(
                Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape)
                    .padding(6.dp)
                    .background(BrandRed, CircleShape)
                    .clickable(enabled = !state.capturing) { capture() },
            )
            Box {
                IconButton(onClick = onReview, enabled = state.pageCount > 0) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.action_done),
                        tint = if (state.pageCount > 0) Color.White else Color.Gray,
                    )
                }
                if (state.pageCount > 0) {
                    Text(
                        state.pageCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(BrandRed, CircleShape)
                            .padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }
}

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
