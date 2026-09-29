# Usability review — PDF Reader & PDF File Edit

Date: 2026-09-29. Method: a screen-by-screen read of the Compose code against the client screenshots
(`docs/screenshots/S01–S13`) and against the published behaviour of Adobe Acrobat Reader, Adobe Scan and
CamScanner. **The app was not run.** This environment cannot reach Google's Maven or the Android SDK, so every
finding comes from reading code, and every fix is unverified on a device.

## 1. Libraries used to open each format

| Format | Opened with | Notes |
|---|---|---|
| PDF (view) | `android.graphics.pdf.PdfRenderer` (part of Android) | Renders pages to bitmaps; adds nothing to the APK size. |
| PDF (search, highlight, sign, text for AI) | **PdfBox-Android** `com.tom-roush:pdfbox-android` 2.0.27.0 (Apache-2.0) | Text extraction, word boxes, `/Highlight` annotations, signed copies. |
| PDF (create from photos/scans) | PdfBox-Android (`pdf/PdfWriter`, `pdf/ImagesToPdf`) | JPEG pages, A4 / Letter / fit. |
| PDF (save AI result) | `android.graphics.pdf.PdfDocument` + `StaticLayout` (Android) | Shapes any script with system fonts (Bengali, Devanagari, CJK). |
| Word `.docx`, Excel `.xlsx`, PowerPoint `.pptx` | **No third-party library** — the app's own converters `office/DocxToHtml`, `XlsxToHtml`, `PptxToHtml` | `java.util.zip` + Android's `XmlPullParser` turn the OOXML into one HTML page, shown in a `WebView` with JavaScript off and labelled "Simplified view". |
| `.doc`, `.xls`, `.ppt` (old binary formats) | Not rendered | "This older format opens in another app" + the system "Open with" chooser. |
| Scanned pages (OCR for AI) | ML Kit Text Recognition (bundled Latin model) | |

Why not other libraries: Apache POI is large and only partly works on Android. MuPDF and iText are AGPL, which
would force the app's source to be published. The client's decision is in TECH_SPEC §1.

## 2. How it compares

✅ has it · 🟡 partly · ❌ missing

| Capability | Acrobat Reader | Adobe Scan | CamScanner | This app |
|---|---|---|---|---|
| All documents on the phone in one list, with type filters | ✅ | — | 🟡 | ✅ |
| PDF continuous scroll, pinch zoom, page n / N | ✅ | — | ✅ | ✅ |
| Go to page | ✅ | — | ✅ | ✅ (fixed now) |
| Find in PDF with match count | ✅ | — | ✅ | ✅ |
| Highlight | ✅ | — | ✅ | ✅ (to a `_highlighted` copy) |
| Underline / strike-through / sticky notes / freehand | ✅ | — | ✅ | ❌ |
| Page thumbnails / outline (bookmarks) | ✅ | — | ✅ | ❌ |
| Remember last page when reopening | ✅ | — | ✅ | ❌ |
| Night / reading mode | ✅ (Liquid Mode, dark) | — | 🟡 | ❌ (app follows system dark theme; pages stay white) |
| Select and copy text | ✅ | — | ✅ | ❌ |
| Fill & sign | ✅ | ✅ | ✅ | ✅ (Pro) |
| Word / Excel / PowerPoint viewing | ❌ (converts) | — | 🟡 | 🟡 simplified HTML |
| Scan: live edge detection + auto-capture | — | ✅ | ✅ | ✅ |
| Scan: on-screen guidance ("Hold steady") | — | ✅ | ✅ | ✅ (fixed now) |
| Scan: crop, rotate, filters, reorder, delete + undo | — | ✅ | ✅ | ✅ |
| Scan: OCR text layer in the saved PDF (searchable scan) | — | ✅ | ✅ | ❌ |
| Scan: rename before saving | — | ✅ | ✅ | ❌ (auto name `Scan yyyy-MM-dd …`) |
| Scan modes (ID card, book, whiteboard) | — | ✅ | ✅ | ❌ (document only, by design) |
| PDF tools: merge, split, compress, rotate pages, password protect | ✅ (paid) | 🟡 | ✅ | ❌ |
| Rename / move files | ✅ | ✅ | ✅ | ❌ |
| On-device translation and summary | 🟡 (cloud AI) | — | 🟡 (cloud) | ✅ (on device) |
| First-run tour | ✅ | ✅ | ✅ | ❌ (only the storage explainer) |

## 3. Problems found and fixed (Steps 9a and 9b)

Ranked by how much each one would confuse or hurt a user.

1. **The whole UI sat under the system bars.** Edge-to-edge is on and forced on Android 15+, but the bars are
   custom rows, so the search, crown and back icons were under the clock and the tabs under the gesture bar.
   Fixed once in `AppNavHost`.
2. **PDF password shown in plain text.** Now masked, with a show/hide toggle.
3. **Selection mode had no exit.** The select icon showed checkboxes with no count, no close and no working Back.
   Fixed; Select all added.
4. **Leaving the camera silently deleted every scanned page.** It now asks "Discard scan?" with "Keep and review".
   The review screen's Discard also asks (FR-044 AC required it).
5. **Leaving Edit/Sign silently dropped every placed signature.** It now asks. Deleting a saved signature was a
   20 dp button that acted immediately; it is now a larger target with a confirmation.
6. **"No documents yet" while the phone was still being scanned.** A spinner and "Looking for documents…" now show.
7. **System Back left the PDF with search or highlight mode open** instead of closing the mode. Fixed, and the
   same for the Office viewer's search.
8. **Unlabelled icons in the camera, scan review and sign tools** (timer, bolt, "+", pen, image, "T").
   They now have text labels: Auto/Manual, Flash on/off, Import, Crop/Apply, Rotate, Delete, Add page,
   Draw signature, Import from photo, Add text.
9. **No capture feedback.** The shutter now blinks white with a haptic tick, and its ring fills while
   auto-capture waits for the page to hold still. The camera shows "Point the camera at a document" /
   "Hold steady…" / "Capturing…".
10. **Highlight mode looked like the normal reader.** It now shows "Drag over the text you want to highlight,
    then tap ✓ to save".
11. **Empty tabs were one grey line.** They now have an icon, a title, an explanation, and the next step
    (Allow access / Scan document / Image to PDF).
12. **A drawn signature was not placed.** It is now put on the current page straight away, as in Fill & Sign.
13. **Search** opens the keyboard at once and says when nothing matches. In the reader, "0" before typing is gone
    and the arrows are disabled with no matches. The ⋮ on search results (it opened the file) is hidden.
14. **File info** now shows the PDF page count.
15. **Accessibility:** tabs are announced as tabs with their selected state; the shutter, match arrows, close
    buttons and password toggle have spoken labels.
16. **Reordering** is now drag-and-drop (Step 8), and Image to PDF tiles show page numbers.

## 4. Gaps still open (recommended order)

| # | Gap | Why it matters | Effort |
|---|---|---|---|
| 1 | Build and test on a device | Nothing in this review has been run; the camera outline in particular maps analysis-frame coordinates onto a cropped preview and may be offset. | — |
| 2 | Remember the last page per PDF | Everyone expects to resume where they stopped. | S |
| 3 | Page thumbnail grid + outline (bookmarks) in the reader | Long PDFs are hard to move around without it. | M |
| 4 | Rename before saving a scan / image PDF, and rename in the file menu | Auto names like `Scan 2026-09-29 18.04.11.pdf` are hard to find later. | S |
| 5 | Searchable scans (OCR text layer on save) | Adobe Scan and CamScanner both do it; the OCR engine is already in the app. | M |
| 6 | Text selection and copy in the PDF reader | Commonly expected. | M |
| 7 | XLSX sheets as tabs (FR-034 says tabs; they are headings today) | Spec gap. | S |
| 8 | Night mode for pages (invert render) | Reading comfort. | S |
| 9 | More markup (underline, strike, note) | Reader parity. | M |
| 10 | PDF tools: merge, split, compress, rotate pages | Main reason people pick CamScanner; not in the BRD, so a scope decision for the client. | L |
| 11 | First-run tour (3 screens) | Helps first-time users find Create and AI. | S |
| 12 | UI languages beyond English | Open question in the BRD. | M |
