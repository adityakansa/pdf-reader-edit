# BRD — PDF Reader & PDF File Edit

Business requirements: **why** and **what**. How = `TECH_SPEC.md`. Executable items = `FR_CHECKLIST.md`.
Screenshots referenced as S01–S13 live in `docs/screenshots/`: S01 select-page ad, S02 search, S03 AI Assistant dialog, S04 translate language sheet, S05 quit-translating dialog, S06 settings, S07 home All, S08 PPT, S09 Word, S10 Excel, S11 Create PDF sheet, S12 Word with the ad slot unfilled, S13 Favourite.

## 1. Problem

People get PDFs, Word, Excel and PowerPoint files from mail, chat and downloads, and they end up scattered
across phone storage. They need one fast app that finds all of them, opens them, turns paper or photos into PDFs,
signs forms, and quickly understands or translates a document — without uploading private files anywhere.

## 2. Users

| User | Typical job |
|---|---|
| Student | Open lecture PDFs/slides, scan notes to PDF, get a short summary of a page, translate a paper page |
| Professional | Find a contract, sign it, share it by mail; open XLSX/PPTX received on the go |
| Daily document user | Bills, receipts, ID copies: scan, find later, share, delete clutter |

## 3. Goals

- G1 Every document on the phone is visible in one list within seconds of opening the app.
- G2 PDFs of any size open smoothly (zoom, search, highlight).
- G3 Paper → PDF (scan) and images → PDF in under a minute.
- G4 Sign a PDF with a saved signature.
- G5 On the phone, offline: a 2–3 paragraph summary of the selected PDF page, plus translation. No document text leaves the device. A made-up detail in the summary is acceptable.
- G6 Revenue: AdMob ads for free users; "PDF Pro" subscription (with Play free trial) removes ads and unlocks premium.
- G7 Pass Google Play review first time.

## 4. Scope

**In scope:** file library (browse/search/sort/favourite/recent/share/delete), PDF reader, DOCX/XLSX/PPTX viewer,
Create PDF (Image to PDF, Scan Document), digital signature and text stamp on PDFs, on-device AI Translate and
AI Summary, settings (Language, Rate Us, Share, Privacy policy), PDF Pro paywall, ads, UMP consent.

**Out of scope (v1):** cloud storage or accounts, any server-side AI, editing document text/layout, creating or
editing Office files, PDF merge/split/compress/password tools, OCR as a standalone feature, legacy binary
`.doc/.xls/.ppt` rendering (see §6 R3-5), tablets-specific layouts, Wear/TV.

## 5. Store copy → requirement trace

| Store copy claim | Requirement |
|---|---|
| "Open and read PDFs of any size smoothly and without lag" | R3-1, NFR-1 |
| "zoom, search 🔎, and highlight tools" | R3-2, R3-3, R3-4 |
| "Supports multiple formats including DOCX, XLS, PPT, and more" | R3-5 |
| "Document Scan: paper → sharp PDFs" | R4-2 |
| "Image to PDF" | R4-1 |
| "Manage & Organize: Delete…" | R2-7 |
| "Advanced Sorting: name, date, or size" | R2-4 |
| "Effortless Sharing" | R2-7 |
| "Digital Signature" | R5-1..R5-3 |
| "Smart AI Assistant: Summarize or translate PDFs" | R6-1..R6-6 |
| "robust search 🔍 to find documents" | R2-5 |

## 6. Functional requirements (business level)

### R1 Shell (S07, S11, S13)
- R1-1 Bottom navigation: **Document**, **Recent**, centre red **Create** button (scan icon), **Favourite**, **Setting**.
  Selected tab = red icon + red label + short red bar above the icon.
- R1-2 Top bar on Document/Recent/Favourite: search icon, gold crown (opens PDF Pro), title "PDF **Reader**"
  ("Reader" in red), sort icon, select/edit icon.
  *Assumption: the rightmost list-with-pen icon enters multi-select mode.*
- R1-3 Tapping Create opens the **Create PDF** bottom sheet: *Image to PDF*, *Scan Document* (S11).

### R2 File library (S02, S07–S10, S12, S13)
- R2-1 Type chips under the top bar: **All, PDF, Word, Excel, PPT**. Selected chip is tinted with the type colour
  (All pink/red, Word blue, Excel green, PPT orange).
- R2-2 Row: coloured type badge (PDF red, DOC blue, XLS green, PPT orange), file name with extension, `yy/MM/dd • size`,
  star (favourite), ⋮ menu.
- R2-3 Document tab lists every PDF, Word, Excel and PowerPoint file on the phone's shared storage (Downloads, Documents, and other public folders), the same way as S07–S10. Recent lists files opened in the app, newest first. Favourite lists starred files. All three use the same chips.
- R2-4 Sort by name, date modified, size; ascending/descending; remembered.
- R2-5 Search screen (S02): back arrow + "Search in file" field with clear button; results filter by name live.
- R2-6 Favourite: star toggles from any row; filled star in Favourite.
- R2-7 ⋮ menu: Share, Delete (with confirmation), File info. Multi-select: Share / Delete many.
- R2-8 When a list would be empty, it shows a bundled **Sample File** of that type (S08, S10, S13: "Sample File
  01/01/2023 • 1KB") so the user can try the feature. *Assumption: this is the reason sample rows appear.*

### R3 Reader
- R3-1 PDF: continuous vertical scroll, pinch zoom, page indicator, password-protected PDFs.
- R3-2 Search inside a PDF with hit highlighting and next/previous.
- R3-3 Highlight selected text; saved into the PDF as a standard highlight annotation.
- R3-4 Reader actions: share, favourite, AI Assistant, Edit (sign / text) — Edit is Pro.
- R3-5 DOCX, XLSX, PPTX open in-app (read-only, text + tables + images; not pixel-perfect).
  Legacy `.doc/.xls/.ppt` appear in the list and open via "Open with another app" in v1.
- R3-6 The user can set this app as the default opener for PDF, Word, Excel and PowerPoint (including `.doc/.xls/.ppt`). The system "Open with" sheet lists the app; "Always" makes it the default for that type. Settings has a "Set as default" row that opens the system screen for this.

### R4 Create PDF (S11)
- R4-1 **Image to PDF:** pick several images, reorder, choose page size/margin, save as PDF.
- R4-2 **Scan Document:** camera with live page outline, auto-crop, manual corner adjust, rotate, filters
  (original / enhanced / grey / B&W), multi-page, save as PDF.
- R4-3 Created PDFs are saved to a visible folder (`Documents/PDF Reader`) and immediately appear in the library.

### R5 Digital signature (Pro "Edit", S06)
- R5-1 Draw a signature (undo / clear / save); keep up to 3; delete; import one from a photo of ink on paper.
- R5-2 Place a signature or a text/date stamp on any PDF page; move, resize, rotate.
- R5-3 Save as a new PDF (`<name>_signed.pdf`); original untouched.
- *Assumption: the Pro banner's "Edit" covers signature and text stamp; highlight stays free (it is a reader tool in the store copy).*

### R6 AI Assistant (S01, S03, S04, S05)
- R6-1 Red floating **AI Assistant** button with crown badge on the Document tab; opens dialog with
  **AI Translate** (blue) and **AI Summary** (purple) (S03).
- R6-2 Flow: choose a PDF (file list, S02 style) → **Select page** (S01) → action screen.
- R6-3 Select page: thumbnail grid, round check per page, header "N page(s)" left and "Choose N page(s)" right,
  full-width **Select** button disabled until a page is picked; bottom native ad for free users.
  *Assumption: "Choose 1 page" is the free-tier page limit; Pro raises it.*
- R6-4 AI Translate screen (S04): file card (thumbnail, "name.pdf - (Page n)", "size - N page"), **Translate To**
  dropdown, help "?" in the top bar, Translate action, result with copy/share/save. The language sheet lists only
  Italian, Polish, Portuguese, Dutch, French, German and Spanish. *French, German and Spanish are the extra
  European languages; S04's longer list is not shipped.* A language's model (~30 MB) downloads only when the user picks
  that language, then works offline. One Portuguese entry (Portugal and Brazil share one model).
- R6-5 AI Summary: same flow. Output is 2 or 3 paragraphs about the selected page(s). Copy/share/save. Invented details are acceptable; there is no accuracy target.
- R6-6 Back during page selection or processing shows **Quit Translating** / "Are you sure you want to quit and
  discard the changes?" with Cancel / Quit (red) (S05). Same dialog wording adapted for Summary.
- R6-7 Everything runs on the phone. Language models are downloaded once, then work offline. Limits (languages,
  pages per run, supported devices) are shown to the user, never hidden behind a cloud fallback.

### R7 Settings & PDF Pro (S06)
- R7-1 Red **PDF Pro** card: crown, "No Ads & Unlock Premium Features", white **Free Trial** button, feature row
  AI Summary · AI Translate · Ads-Free · Edit.
- R7-2 Rows: Language (shows current), Set as default (R3-6), Rate Us, Share, Privacy policy. A "Privacy options" row appears only when the
  consent framework requires it (EEA/UK).
- R7-3 Paywall discloses trial length, price after trial, renewal period and how to cancel before purchase.
- R7-4 Crown marks (top bar, AI button, banner) are shown only to non-Pro users.

### R8 Ads (exact placements — no other ad units)
| Placement | Screens | Evidence |
|---|---|---|
| Bottom anchored banner (creative shows e.g. Google logo + "Open"; AdChoices ⓘ top-right) | Document, Recent, Favourite, Setting tabs; Search screen | S02, S06, S07–S10, S13 |
| Bottom small native ad with "Ad" badge and loading placeholder | Select page | S01 |
- Slot collapses when no ad fills (S12). No ads for Pro. No ads on reader, camera, editor, AI processing/result, paywall.
- *Assumption: the "AI"/ⓘ badge on the ad bar is the SDK's AdChoices marker, not an app element.*
- No app-open, interstitial or rewarded ads (not shown in the reference).

## 7. Non-functional requirements

- NFR-1 Performance: cold start ≤ 1.5 s to a populated list (mid-range, 1,000 files); first PDF page ≤ 1 s for a 50 MB
  file; 60 fps scrolling in list and reader; scan shutter-to-review ≤ 1.5 s.
- NFR-2 Offline: reader, scan, image-to-PDF, signature and AI (after model download) work in airplane mode.
- NFR-3 Privacy: no document content, page images or extracted text is sent to any server or SDK. Ads/billing SDKs
  receive only what they need to serve ads and subscriptions.
- NFR-4 Permissions: ask only when the feature is used (camera on first scan, file access on first launch with a
  clear explanation and a picker-based fallback).
- NFR-5 Quality: crash-free ≥ 99.5 %; no ANR from file scanning, PDF writing or AI (all off the main thread).
- NFR-6 Size: the summary model (~65.5 MB) is installed with the app, so the Play download is over 60 MB. Translation models (~30 MB each) still download only for a language the user picks.
- NFR-7 Accessibility: 48 dp touch targets, content descriptions, text contrast ≥ 4.5:1.
- NFR-8 Localisation-ready: all strings in resources; in-app language picker.
