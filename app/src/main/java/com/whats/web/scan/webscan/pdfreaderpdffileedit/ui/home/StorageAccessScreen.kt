package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/**
 * FR-010 / FR-101. The explainer the user sees before the system page, and the SAF way out when they
 * say no (or when the build ships without all-files access at all).
 */
@Composable
fun StorageAccessScreen(
    onDone: () -> Unit,
    viewModel: StorageAccessViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh(); onDone() }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.persist(uri)
        onDone()
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach(viewModel::persist)
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = BrandRed,
            modifier = Modifier.size(64.dp),
        )
        Text(
            stringResource(R.string.storage_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            stringResource(R.string.storage_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        if (viewModel.allFilesAccessAvailable) {
            Button(
                onClick = {
                    viewModel.markShown()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    } else {
                        legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.storage_grant)) }
        }
        TextButton(onClick = { viewModel.markShown(); pickTree.launch(null) }) {
            Text(stringResource(R.string.storage_pick_folder))
        }
        TextButton(onClick = { viewModel.markShown(); pickFiles.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.storage_add_files))
        }
        TextButton(onClick = { viewModel.markShown(); onDone() }) {
            Text(stringResource(R.string.storage_skip))
        }
    }
}
