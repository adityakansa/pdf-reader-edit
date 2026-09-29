# Screen-by-screen match: One Read screenshots (2026-09-29)

The screenshots added to `docs/screenshots/` on 2026-09-29 are `photo_61879594944569568NN_y.jpg`, NN = 51–72.
Each row says what this app shows for that screen now, and where it lives.

| # | Their screen | This app | Where |
|---|---|---|---|
| 51 | Home tools: Create & Convert, Edit & Manage grids, Recycle bin | ✅ same grids, same tools, same order, plus an "AI tools" row (on-device translate, summary, extract text) | `ui/home/Dashboard`, `Catalogue` |
| 52 | "PDF files" list: type icon, name, 09/29/2026 + size, ☆, ⋮; bar with sort and search | ✅ same rows (drawn PDF/W/X/P/T icon), same bar with select, sort, search | `FileListScreen`, `FileRow` |
| 53 | Notification permission prompt | ⏭ not copied on purpose: this app sends no notifications, and asking for a permission it does not use hurts trust and Play review | — |
| 54 | "Word files" list | ✅ | `FileListScreen` |
| 55 | Home: "Set as default reader" banner [Set] ✕, "All Files" cards with counts (All, PDF, Word, Excel, PPT, TXT, Directories + size, Favorites), 3-tab bar | ✅ all of it; the banner hides itself once closed or when this app already opens PDFs | `Dashboard`, `BottomBar` |
| 56 | Viewer of a photographed PDF with "Convert to Word" at the bottom | ✅ shown when the PDF's first page has no text | `PdfReaderScreen` |
| 57 | Viewer bar: PDF→Word, OCR, rotate, reading mode, share, ⋮ | ✅ W (PDF to Word), search, share, ⋮ (extract text/OCR, translate, summary, bookmarks, pages, print, sign …); red pen FAB to edit | `PdfReaderScreen` |
| 58 | "Are you satisfied with One Read?" Good / Not really | ✅ once, after three documents were opened; Good → Play in-app review, Not really → feedback mail | `RatingSheet` |
| 59 | "Converting… 30%" | ✅ with a smoothly filling bar and Cancel | `ConvertScreen` |
| 60 | "Converted successfully!", name ✏, View locally, Share, Open, "Are you satisfied with PDF to Word?" | ✅ all of it | `ConvertScreen` |
| 61 | PDF editor intro: "Edit and format text easily." Try now / Skip | ✅ first time only | `PdfEditorScreen` (`EditorIntro`) |
| 62 | Editor: ✕, 💡, pages, bar Edit text · Add text · Add image · Annotate · Fill & Sign | ✅ same bar; plus Undo, Redo and Save | `PdfEditorScreen` |
| 63 | Edit text: box with handles on a table cell, keyboard, formatting bar | ✅ dashed boxes per line or cell, handles to move and resize, bar: size ±, colour, font, bold, italic, delete | `PdfEditorScreen`, `PdfPageEditor` |
| 64–65 | Pinch zoom "150%" while editing | ✅ | `PdfEditorScreen` |
| 66 | Annotate bar: copy, highlighter, text colour, strike, pen, eraser | ✅ highlight, underline, strike, pen (colours), **eraser**, undo | `AnnotateBar`, `MarkupEraser` |
| 67, 69 | Settings: Remove ads card; General (File Manager, Keep screen on, Scan crop settings, Default reader, Share App); Display (App theme, Language); Help (FAQ, Request a feature, Feedback, Terms, Privacy); version | ✅ all except Scan crop settings (the scanner always shows the detected edges to adjust, so there is nothing to choose); plus Recycle bin, Rate, licences | `SettingsScreen` |
| 68 | Recent: All / PDF / Word / Excel / PPT tabs, "1 minute ago" | ✅ plus a TXT tab | `HomeScreen` (`TypeTabs`) |
| 70 | Dark "Get Premium": benefits, trial plan with "Save 74%", monthly, badge, review, Start Free Trial, Cancel anytime, Terms, Privacy, ✕, Restore | ✅ same layout, prices and per-day prices from Play. The review card and "Trusted by millions" are **not** copied: they would be invented. A true "Private by design" line is shown instead | `PaywallScreen`, `PlanMath` |
| 71 | Viewer converting in place, red pen FAB | ✅ FAB opens the editor; conversions show on their own screen | `PdfReaderScreen` |
| 72 | "The recycle bin is empty" | ✅ and a working 30-day bin with restore | `RecycleBinScreen`, `RecycleBin` |

Icons: no icon or UI/UX skill is installed on the account, so the icons are Material Symbols (every name checked
against the real `material-icons-extended` jar) plus drawn ones: the file-type document icon and the eraser.
