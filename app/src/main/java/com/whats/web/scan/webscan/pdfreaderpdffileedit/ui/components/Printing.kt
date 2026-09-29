package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import java.io.FileOutputStream

/**
 * Printing through Android's print service (any Wi-Fi printer, or "Save as PDF"). A PDF is handed over
 * byte for byte; converted Office and text files print what the viewer shows.
 */
object Printing {

    fun printPdf(context: Context, uri: Uri, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return unavailable(context)
        runCatching { manager.print(jobName, PdfFileAdapter(context.applicationContext, uri, jobName), null) }
            .onFailure { unavailable(context) }
    }

    fun printWebView(context: Context, webView: WebView, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return unavailable(context)
        runCatching {
            manager.print(jobName, webView.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
        }.onFailure { unavailable(context) }
    }

    private fun unavailable(context: Context) {
        Toast.makeText(context, R.string.print_unavailable, Toast.LENGTH_LONG).show()
    }

    /** Streams the PDF to the print spooler; the page count is left to the print service to read. */
    private class PdfFileAdapter(
        private val context: Context,
        private val uri: Uri,
        private val name: String,
    ) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(if (name.endsWith(".pdf", true)) name else "$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback,
        ) {
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot read $uri" }
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            if (cancellationSignal.isCanceled) {
                                callback.onWriteCancelled()
                                return
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    private const val BUFFER = 64 * 1024
}
