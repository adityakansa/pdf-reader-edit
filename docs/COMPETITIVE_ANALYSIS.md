# Competitive analysis — scanners, readers, AI (2026-09-29)

Researched on the web on 2026-09-29. The Adobe help pages themselves are blocked from this environment, so Adobe
details come from search summaries of those pages and the Play listing. Star counts were read from GitHub on the same day.

## 1. What the leaders ship

| Area | Adobe Scan (+ Acrobat) | CamScanner | This app today |
|---|---|---|---|
| Capture | AI edge detection, perspective fix, auto-capture, multipage | Auto-crop, edge detection, filters, multipage, shadow/uneven-light cleanup | Edge detection (DocAligner + OpenCV), auto-capture, 4 filters, crop, rotate, drag reorder |
| OCR | Searchable PDFs; "image to text" | OCR incl. **handwriting** | **Extract text** (text layer, or ML Kit Latin OCR for scanned pages) — Step 10a. Saved scans are not searchable yet. |
| AI | **AI Assistant**: ask questions by text or voice, answers with numbered **citations** that jump to the source; one-tap **generative summary**; premium with a free question quota | **"Chat with Docs"** (Mar 2026): questions, summaries, key insights | On-device **Translate** (7 languages, ML Kit) and **Summary** (96M-parameter LLM via llama.cpp). No document Q&A. Nothing leaves the phone. |
| File list | Recent / All scans; **page thumbnails**; tap-to-select; sort by name or date; quick actions after a scan | Folder-based, thumbnails, tags | **First-page thumbnails, page count, list/grid switch** (Step 10a), type chips, sort, favourites, recents, multi-select |
| Editing | Modify scan later (reorder, crop, enhance); Fill & Sign | Watermark, signature, annotate | Highlight, signature/text stamps (Pro) |
| PDF tools | Merge, split, compress, protect (Acrobat, paid) | Merge, split, compress, password | **Merge, extract, rotate, delete pages, add/remove password** (Step 10b); compress not yet |
| Privacy | Cloud (Adobe Document Cloud) | Cloud sync | **Fully on device** — our strongest differentiator |

Sources: [Adobe Scan AI Assistant announcement](https://community.adobe.com/announcements-516/ask-your-scans-anything-meet-adobe-scan-ai-assistant-1561233),
[Adobe help: AI Assistant in Adobe Scan](https://helpx.adobe.com/acrobat/using/ai-assistant-adobe-scan.html),
[Adobe Scan on Google Play](https://play.google.com/store/apps/details?id=com.adobe.scan.android&hl=en_US),
[Adobe Scan: Manage scans](https://www.adobe.com/devnet-docs/adobescan/android/en/findingfiles.html),
[CamScanner on Google Play](https://play.google.com/store/apps/details?id=com.intsig.camscanner&hl=en_US),
[CamScanner AI in 2026 (Nerdbot)](https://nerdbot.com/2026/03/29/beyond-ocr-how-camscanners-ai-scanner-app-is-revolutionizing-handwritten-knowledge-in-2026/).

## 2. Open-source projects worth learning from

| Project | Stars | License | What is worth taking (ideas only — no code copied) |
|---|---|---|---|
| [Stirling-PDF](https://github.com/Stirling-Tools/Stirling-PDF) | ~90k (reported) | see repo | The PDF tool catalogue: merge, split, rotate, reorganise, compress, password add/remove, watermark, redact, OCR. It is a server app, so only the feature list applies. |
| [OSS-DocumentScanner](https://github.com/Akylas/OSS-DocumentScanner) | 2.5k | MIT | OpenCV + Tesseract OCR, QR/barcode detection on scans, and a "card wallet" mode. |
| [FairScan](https://github.com/pynicolas/FairScan) | ~900 | GPL-3.0 | The **design principle**: "a clean, shareable PDF in seconds, with no manual adjustments". Few controls, strong defaults. GPL, so its code cannot go into this app. |
| [MakeACopy](https://github.com/egdels/makeacopy) | ~550 | Apache-2.0 | **Searchable PDF** export, OCR review with word-level editing, an accessibility mode with haptic feedback, fully offline (PaddleOCR). |

Sources: [GitHub topic: document-scanner](https://github.com/topics/document-scanner),
[Stirling-PDF README](https://github.com/Stirling-Tools/Stirling-PDF/blob/main/README.md),
[Stirling-PDF overview (BestHub)](https://www.besthub.dev/articles/stirling-pdf-92k-star-open-source-pdf-toolbox-free-local-alternative-to-adobe-wps-d2357a52cf2e).

## 3. Animation and UI libraries

- **Jetpack Compose animation APIs (already in the app, no new dependency):** `animateItem()` for list reorder,
  insert and delete, `AnimatedContent` / `Crossfade` for state changes, `Animatable` for the shutter flash. Step 10a uses
  these for thumbnail fade-in and list and grid item movement.
- **Lottie** (`com.airbnb.android:lottie-compose`, Apache-2.0, the most-used Android animation library) fits
  illustrated empty states and onboarding. It is not added yet: every new dependency must first be built in a real
  Gradle run, and this environment cannot reach Google's Maven.

## 4. Roadmap from this analysis (value ÷ effort, highest first)

| # | Feature | Peers | Feasible with what is already in the app | Status |
|---|---|---|---|---|
| 1 | Page thumbnails + list/grid, Adobe-style rows | Adobe, CamScanner | PdfRenderer | ✅ Step 10a |
| 2 | Extract text (OCR / text layer) → copy, share, save | Adobe, CamScanner | ML Kit OCR + PdfBox | ✅ Step 10a |
| 3 | PDF tools: merge, split / extract pages, rotate pages, delete pages, add or remove password | Acrobat, CamScanner, Stirling | PdfBox (merger, splitter, protection policy) | ✅ Step 10b |
| 4 | Searchable scans (invisible OCR text layer on save) | Adobe, CamScanner, MakeACopy | ML Kit OCR + PdfBox | planned |
| 5 | Ask-your-document (Q&A) | Adobe AI Assistant, CamScanner Chat | The 96M on-device model is too weak for reliable answers; a keyword finder with page citations over the text index is honest and feasible | to decide |
| 6 | Shadow / uneven-light cleanup filter | CamScanner | OpenCV (already bundled) | planned |
| 7 | QR / barcode detection in scans | OSS-DocumentScanner | ML Kit barcode (new dependency) | later |
| 8 | Compress PDF | CamScanner, Stirling | PdfBox image re-encode | later |
| 9 | Lottie empty states / onboarding | — | new dependency | after a device build |

## 5. Parity with "Document Reader - PDF Editor" (Simple Design)

Checked against the features in its published description (`alldocumentreader.office.viewer.filereader`). Its
Play page and screenshots cannot be reached from this environment, so its exact screens have not been compared.

| Their feature | This app | Step |
|---|---|---|
| Open PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT | ✅ all of them in-app, including Office 97–2003 and CSV | 11a, 11e |
| Word shown with formatting and layout | ✅ page cards, styles, fonts, colours, lists, tables, images | 11b |
| Excel in an easy grid | ✅ spreadsheet grid with column letters, row numbers, sheet tabs | 11a |
| PowerPoint slides "with high clarity" | ✅ real slides with positioned text, shapes and pictures | 11b |
| Auto-scan the phone and organise by type | ✅ type chips + page-preview list/grid | 10a |
| Folder structure view | ✅ Folders view | 11d |
| Search by name | ✅ | — |
| Search text in all documents | ✅ "Inside files" with snippets, PDFs open at the page | 11d |
| Bookmark pages | ✅ bookmarks + page thumbnail navigator + go to page | 11a |
| Highlight, underline, strikethrough, doodle | ✅ Annotate: highlight, underline, strike, pen (3 colours), eraser, undo | 11c, 12b |
| PDF editor: edit text, add text, add image | ✅ retype any line (old words removed), size/colour/font/bold/italic, add text, add pictures | 12b |
| Signature | ✅ (Pro) | 4 |
| Merge / split PDF | ✅ merge; extract, rotate, delete pages | 10b |
| Print | ✅ PDFs and every viewer page | 11a |
| Share | ✅ | — |
| Scan to PDF, images to PDF | ✅ with edge detection, filters, reorder | 3 |
| Create new documents / edit | ✅ new document (Word, PDF or text) with headings, bold, italic, lists; edit .txt files | 11e |
| Full-screen ads (a common review complaint) | ✅ none: banner + one native ad only | — |
| **Extra here:** on-device translate, summary, OCR text extraction, PDF passwords, resume last page | — | 5, 9c, 10a, 10b |

Not matched: editing existing Word/Excel/PowerPoint content in place (would need a full office editor), and
exact visual fidelity for complex Office layouts (charts, SmartArt, text boxes, headers/footers).
