package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy

/**
 * PdfBox refuses to save a document it decrypted ("PDF contains an encryption dictionary") until it is told
 * what to do with the security. Every tool that saves a loaded PDF calls [prepareForSave] first.
 */
internal object PdfSecurity {

    /**
     * A PDF that needed a [password] to open is locked again with that same password, so an edited copy of
     * a protected file is still protected. One that only carried an owner password (print or copy
     * restrictions, but it opens without a password) is saved without them; the user is editing it on
     * purpose, and PdfBox cannot keep an owner password it was never told.
     */
    fun prepareForSave(document: PDDocument, password: String?) {
        if (!document.isEncrypted) return
        if (password.isNullOrEmpty()) {
            document.isAllSecurityToBeRemoved = true
            return
        }
        val keyLength = document.encryption?.length?.takeIf { it in KEY_LENGTHS } ?: DEFAULT_KEY_LENGTH
        document.protect(
            StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = keyLength },
        )
    }

    private val KEY_LENGTHS = setOf(40, 128, 256)
    private const val DEFAULT_KEY_LENGTH = 128
}
