package com.whats.web.scan.webscan.pdfreaderpdffileedit

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.AppNavHost
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ShellViewModel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: ShellViewModel by viewModels()

    @Inject
    lateinit var incoming: IncomingFile

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AppTheme {
                AppNavHost(viewModel = viewModel)
            }
        }
        viewModel.onStart(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    /** FR-021: a document handed to us by another app opens in the matching viewer. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        lifecycleScope.launch { incoming.offer(uri, intent.type) }
    }
}
