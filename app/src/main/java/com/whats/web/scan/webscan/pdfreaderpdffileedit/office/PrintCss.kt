package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

/**
 * Step 12c (Word / Excel / PowerPoint to PDF). The viewer's HTML is made for a phone screen: grey desk,
 * page cards, one sheet visible at a time, dark mode. Printed to PDF it should instead look like the
 * document: white A4 pages with margins, every sheet one after another without row and column headers,
 * and one slide per landscape page. This rewrites the viewer HTML for print; nothing else changes.
 */
object PrintCss {

    enum class Kind { DOCUMENT, SHEET, SLIDES, TEXT }

    fun forPrint(html: String, kind: Kind): String {
        // An unknown media feature value turns the whole dark-mode block off, whatever the phone's theme.
        var light = html.replace("@media (prefers-color-scheme: dark)", "@media (prefers-color-scheme: print-never)")
        if (kind == Kind.SLIDES) {
            // On screen a slide's height is a padding percentage; print needs a real height or Chromium
            // splits the slide across two pages.
            light = SLIDE_PADDING.replace(light) { m ->
                val percent = m.groupValues[1].toFloatOrNull() ?: 56.25f
                "class=\"deck-slide\" style=\"padding-top:0;height:${SLIDE_WIDTH_MM * percent / 100f}mm;\""
            }
        }
        val css = "<style>${base(kind)}</style>"
        val at = light.lastIndexOf("</head>")
        return if (at >= 0) light.substring(0, at) + css + light.substring(at) else css + light
    }

    private val SLIDE_PADDING = Regex("class=\"deck-slide\" style=\"padding-top:([0-9.]+)%;\"")
    private const val SLIDE_WIDTH_MM = 250f

    private fun base(kind: Kind): String = buildString {
        append(if (kind == Kind.SLIDES) "@page { size: A4 landscape; margin: 8mm; }" else "@page { size: A4; margin: 16mm 14mm; }")
        append(
            "html, body { background: #ffffff !important; color: #000000; }" +
                "body, body.desk, body.deck { padding: 0 !important; margin: 0 !important; }" +
                "* { -webkit-print-color-adjust: exact; print-color-adjust: exact; }",
        )
        when (kind) {
            Kind.DOCUMENT -> append(
                ".doc-page { box-shadow: none !important; border: 0 !important; border-radius: 0 !important;" +
                    " margin: 0 !important; padding: 0 !important; max-width: none !important; width: auto !important; }" +
                    ".doc-page + .doc-page { break-before: page; }" +
                    "p, li, h1, h2, h3, h4 { orphans: 2; widows: 2; } h1, h2, h3, h4 { break-after: avoid; }" +
                    "tr, img { break-inside: avoid; }",
            )
            Kind.SHEET -> append(
                ".sheet-radio, .sheet-tabs, .grid thead, .grid th.rownum, .grid th.corner { display: none !important; }" +
                    ".sheet-panel { display: block !important; }" +
                    ".sheet-panel + .sheet-panel { break-before: page; }" +
                    ".grid-wrap { overflow: visible !important; margin: 0 !important; }" +
                    "table.grid th, table.grid td { position: static !important; }" +
                    ".grid { border-collapse: collapse; font-size: 9pt; width: auto; }" +
                    ".grid td { border: 0.5pt solid #bfbfbf; padding: 2pt 4pt; white-space: nowrap; }" +
                    "tr { break-inside: avoid; }",
            )
            Kind.SLIDES -> append(
                ".slide-number { display: none !important; }" +
                    // Slides are sized by padding (height 0), which print fragmentation mis-measures with
                    // break-after; a break before every slide but the first gives exactly one per page. 250 mm wide keeps
                    // even a 4:3 slide (187.5 mm tall) inside the 194 mm a landscape A4 page leaves.
                    ".deck-slide { box-shadow: none !important; border: 0.5pt solid #d0d0d0; margin: 0 auto !important;" +
                    " width: ${SLIDE_WIDTH_MM}mm !important; break-inside: avoid; }" +
                    ".deck-slide ~ .deck-slide { break-before: page; }",
            )
            Kind.TEXT -> append("pre, .text { white-space: pre-wrap; word-wrap: break-word; font-size: 10.5pt; }")
        }
    }
}
