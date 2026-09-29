package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R

/** FR-104. The licence notices the bundled models and libraries require. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notices)) },
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(NOTICES, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val NOTICES = """
Apache PDFBox (PdfBox-Android) — Apache License 2.0
OpenCV — Apache License 2.0
DocAligner (document corner model) — see NOTICE_DOCALIGNER in the app assets
LiteRT / TensorFlow Lite — Apache License 2.0
Google ML Kit — subject to the Google APIs Terms of Service; text recognition, language
identification and translation run on this device
llama.cpp — MIT License, Copyright (c) 2023-2024 The ggml authors
Minueza-2-96M-Instruct-Variant-04 (summary model) — Apache License 2.0,
Copyright the model authors. The model file ships inside this app and is never
queried over the network.

Apache License 2.0 summary: you may use, reproduce and distribute the work, provided you
retain the notices above. The full licence text is at https://www.apache.org/licenses/LICENSE-2.0

MIT License summary: permission is granted free of charge to use, copy, modify and distribute
the software, provided the copyright notice and this permission notice are included.
"""
