package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-020. One bundled file per type so an empty tab still shows the app working. They are copied out
 * of assets on first use, never deleted by the user, and hidden as soon as a real file of that type
 * exists (the filtering lives in [FileRepository]).
 */
@Singleton
class SampleFiles @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "samples")

    private val assets = listOf(
        "sample.pdf" to DocType.PDF,
        "sample.docx" to DocType.WORD,
        "sample.xlsx" to DocType.EXCEL,
        "sample.pptx" to DocType.PPT,
        "sample.txt" to DocType.TEXT,
    )

    fun list(): List<DocFile> {
        install()
        return assets.mapNotNull { (assetName, type) ->
            val file = File(dir, assetName)
            if (!file.exists()) return@mapNotNull null
            DocFile(
                key = "sample:$assetName",
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                // The screenshots show the bare label, not "Sample File.pptx".
                name = SAMPLE_NAME,
                ext = assetName.substringAfterLast('.'),
                type = type,
                size = file.length(),
                modified = SAMPLE_DATE,
                isSample = true,
            )
        }
    }

    @Synchronized
    private fun install() {
        if (!dir.exists()) dir.mkdirs()
        assets.forEach { (assetName, _) ->
            val target = File(dir, assetName)
            if (target.exists() && target.length() > 0L) return@forEach
            runCatching {
                context.assets.open("samples/$assetName").use { input ->
                    target.outputStream().use(input::copyTo)
                }
            }
        }
    }

    companion object {
        const val SAMPLE_NAME = "Sample File"

        /** The screenshots show samples dated 01/01/2023. */
        val SAMPLE_DATE: Long =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2023-01-01")?.time ?: 0L
    }
}
