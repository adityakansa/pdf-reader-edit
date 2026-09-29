package com.whats.web.scan.webscan.pdfreaderpdffileedit

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android needs its font/CMap resources wired to the asset manager before first use.
        PDFBoxResourceLoader.init(this)
    }
}
