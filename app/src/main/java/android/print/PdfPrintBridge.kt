package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Step 12c. Writes a [PrintDocumentAdapter]'s output (a WebView's, here) straight to a PDF file, with no
 * print dialog. The two callback classes have package-private constructors, which is why this one file
 * lives in `android.print`; it is the long-standing way apps print a WebView to PDF on their own.
 *
 * If a future Android refuses this (an [IllegalAccessError] or similar), the caller catches it and falls
 * back to the system print screen with "Save as PDF".
 */
object PdfPrintBridge {

    fun write(adapter: PrintDocumentAdapter, attributes: PrintAttributes, target: File, done: (Boolean) -> Unit) {
        val cancel = CancellationSignal()
        adapter.onStart()
        adapter.onLayout(
            null,
            attributes,
            cancel,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    val descriptor = try {
                        ParcelFileDescriptor.open(
                            target,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE,
                        )
                    } catch (_: Exception) {
                        adapter.onFinish()
                        done(false)
                        return
                    }
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        descriptor,
                        cancel,
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) = finish(true)
                            override fun onWriteFailed(error: CharSequence?) = finish(false)
                            override fun onWriteCancelled() = finish(false)

                            private fun finish(ok: Boolean) {
                                runCatching { descriptor.close() }
                                adapter.onFinish()
                                done(ok)
                            }
                        },
                    )
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    adapter.onFinish()
                    done(false)
                }

                override fun onLayoutCancelled() {
                    adapter.onFinish()
                    done(false)
                }
            },
            Bundle(),
        )
    }
}
