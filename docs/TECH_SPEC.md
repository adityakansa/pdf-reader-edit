# Tech spec — PDF Reader & PDF File Edit

**How** to build `BRD.md`. Every requirement has an `FR-xxx` ID; acceptance criteria live only in `FR_CHECKLIST.md`.
Reference code: pdfscanner (DocVault) at `/Users/aditya/StudioProjects/pdfscanner` — paths below are relative to it
and prefixed `ps:`.

## 1. Stack decisions

| Concern | Decision | Why |
|---|---|---|
| Modules | Keep the single `:app` module (as the repo is). Two extra Gradle modules, both forced by tooling: `:third-party:opencv` (vendored, generated) and `:ai_summary_model` (install-time Play Asset Delivery pack; the summary model is installed with the app). | Repo is a single-module template; no parallel architecture. |
| Package root | `com.whats.web.scan.webscan.pdfreaderpdffileedit` (existing `namespace`/`applicationId`) | Fixed by `app/build.gradle.kts`. |
| UI | Jetpack Compose + Material 3 inside one `AppCompatActivity` | Scanner/signature/reader UI being reused from pdfscanner is Compose. `AppCompatActivity` (appcompat already in Gradle) gives per-app language on API < 33. |
| DI | Hilt + KSP | Reused pdfscanner classes are `@Inject`-annotated; keeps the port diff small. |
| Persistence | Room (one table) + Preferences DataStore | Favourite/recent metadata and a few settings. No encryption: the app works on the user's own public files. |
| PDF render | `android.graphics.pdf.PdfRenderer` | ps:`docs/pdf-engine-decision.md` — zero APK cost. |
| PDF write/text/annotate | PdfBox-Android (Apache-2.0) | Same decision; no AGPL (MuPDF/iText forbidden). |
| minSdk | Raise 24 → **26** | LiteRT, ML Kit, CameraX setup copied from pdfscanner all target 26. |
| Build | AGP 9.3.3 built-in Kotlin, Java 17 target, compile/target SDK 37 (already set) | Match pdfscanner toolchain. |

Dependencies to add (versions = pdfscanner `gradle/libs.versions.toml` unless marked *pin at implementation*):
Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, navigation-compose 2.10.1, kotlinx-serialization 1.9.0,
coroutines 1.10.2, Hilt 2.60.1 (+ hilt-navigation-compose 1.4.0), KSP 2.3.12, Room 2.8.5, DataStore 1.2.1,
CameraX 1.6.2, pdfbox-android 2.0.27.0, LiteRT 1.4.1, ML Kit text-recognition 16.0.1, Play Billing ktx 9.1.0,
play-services-ads 25.4.0, UMP 4.0.0, play-review 2.0.2, exifinterface 1.4.2, documentfile 1.1.0;
**new vs pdfscanner:** `com.google.mlkit:translate`, `com.google.mlkit:language-id`,
`com.google.android.play:asset-delivery-ktx` — *pin at implementation*; llama.cpp (MIT, `ggml-org/llama.cpp`, tested at
tag `b11259`) vendored as source and built by `:app` CMake with a small JNI wrapper — `libllama` + `libggml*`, arm64-v8a.

## 2. Package layout (`app/src/main/java/com/whats/web/scan/webscan/pdfreaderpdffileedit/`)

```
App.kt                 @HiltAndroidApp
MainActivity.kt        AppCompatActivity, setContent { AppNavHost }
ui/theme/              Color, Type, Theme (FR-002)
ui/components/         FileRow, TypeBadge, TypeChips, ConfirmDialog, BottomSheet, CrownBadge
ui/shell/              AppNavHost, BottomBar, CreateButton, TopBar (FR-003/004)
ui/home/               DocumentTab, RecentTab, FavouriteTab, SortSheet, CreatePdfSheet, SelectionBar
ui/search/             SearchScreen
ui/reader/             PdfReaderScreen, PdfPages, MarkupLayer, OfficeReaderScreen (WebView)
ui/scan/               CameraScreen, CameraViewModel, overlays, review/ (ported, §5)
ui/imagetopdf/         ImageToPdfScreen
ui/sign/               SignaturesScreen, SignaturePad, PlaceOnPdfScreen, AnnotationLayer
ui/ai/                 AiAssistantDialog, AiFilePicker, SelectPageScreen, TranslateScreen, SummaryScreen,
                       AiResultScreen, LanguageSheet, QuitDialog
ui/settings/           SettingsScreen, ProBanner
ui/paywall/            PaywallScreen
data/files/            FileIndex, FileRepository, FileMetaDao/Entity, AppDatabase, SampleFiles, OutputFolder
data/prefs/            AppPreferences (DataStore)
pdf/                   ported ps:core/pdf subset + PdfSigner, TextToPdf (§4, §6, §7)
office/                DocxToHtml, XlsxToHtml, PptxToHtml, OoxmlZip
imaging/               ported ps:core/imaging subset (§5)
ocr/                   TextRecogniser (ported)
sign/                  SignatureStore, AnnotationBounds (ported)
ai/                    PageTextExtractor, Translator, SummaryEngine, SummaryModelDelivery, AiLimits
ads/                   AdConsent, Ads, BannerAd, NativeAdSlot
billing/               BillingManager, Products, Store, SubscriptionOffers, Entitlement (ported)
```

## 3. Shell, library, storage

- **FR-001** Build setup: deps above, `minSdk 26`, Compose + Hilt + KSP + Room plugins, `:third-party:opencv` copied verbatim
  from ps:`third-party/opencv` (NDK r28 build → 16 KB page aligned), `abiFilters arm64-v8a, armeabi-v7a`, R8 on for release
  with keep rules from ps:`app/proguard-rules.pro` (PdfBox, OpenCV, LiteRT, ML Kit).
- **FR-002** Theme: brand red `#D32F2F` (title "Reader", selected tab, FAB `#B71C1C`), type colours PDF `#E53935`,
  Word `#1E6FD9`, Excel `#1E9E5A`, PPT `#F4731F`, chip tints at 12 % alpha, AI Translate light blue / AI Summary light purple
  buttons with 1 dp border, background `#F6F6F6`, cards white 16 dp radius. Font: Plus Jakarta Sans (OFL, bundled) —
  *assumption from screenshots*. All colours in `ui/theme`, never inline.
- **FR-003** `AppNavHost` (type-safe Navigation Compose). Bottom bar = 4 tabs + raised centre button (red circle, scan icon)
  that opens `CreatePdfSheet`. Selected tab shows a 24×3 dp red bar above the icon.
- **FR-004** `TopBar`: search → `SearchScreen`; crown (hidden for Pro) → Paywall; sort → `SortSheet`; select icon → selection mode.
- **FR-005** Per-app language: `AppCompatDelegate.setApplicationLocales`, `res/xml/locales_config.xml`,
  `AppLocalesMetadataHolderService` with `autoStoreLocales=true`. *Open question: UI languages beyond English.*
- **FR-006** Brand launcher icon (adaptive) + `core-splashscreen`; current icon is the Android template.
- **FR-010** Storage access. API 30+: `MANAGE_EXTERNAL_STORAGE` requested from an explainer screen
  (`Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`). API 26–29: `READ_EXTERNAL_STORAGE`
  (`WRITE_EXTERNAL_STORAGE` maxSdk 29, `requestLegacyExternalStorage`). **Fallback when denied or when build flag
  `ALL_FILES_ACCESS=false`:** library = app output folder + files opened via "Open with"/`ACTION_OPEN_DOCUMENT`
  (persisted grants) + folders granted via `ACTION_OPEN_DOCUMENT_TREE`, with an "Add files" action in empty states.
- **FR-011** `FileIndex` scans the phone for documents and lists them like S07–S10. Query
  `MediaStore.Files.getContentUri("external")` for extensions `pdf, doc, docx, xls, xlsx, csv, ppt, pptx`
  (Word = doc/docx, Excel = xls/xlsx/csv, PPT = ppt/pptx). Columns `_id, _data, display_name, size, date_modified, mime_type`.
  Then one walk of the primary shared volume for those extensions MediaStore missed (Download, Documents, `Android/media`);
  skip `Android/data`. `ContentObserver` + refresh on `ON_RESUME`; `Dispatchers.IO`; `Flow<List<DocFile>>`.
  `DocFile(key, uri, name, ext, type, size, modified)`, `type ∈ {PDF, WORD, EXCEL, PPT}`; `key` = content URI string
  (SAF mode: document URI). No plain-text or unrelated files.
- **FR-012/013** Chips + rows as BRD R2-1/R2-2. Date `yy/MM/dd`; size via `Formatter.formatShortFileSize`.
- **FR-014** Sort: `NAME | DATE | SIZE` × asc/desc in DataStore; default DATE desc.
- **FR-015** `SearchScreen`: case-insensitive `contains` on name over the indexed list, debounce 150 ms, type chips not shown (S02).
- **FR-016/017** Room `file_meta(key TEXT PK, favourite INT, favourited_at INT, last_opened_at INT)`. Recent = rows with
  `last_opened_at` joined to the live index (missing files dropped), newest first, cap 100.
- **FR-018** Share: `ACTION_SEND` with the content URI + `FLAG_GRANT_READ_URI_PERMISSION` (FileProvider for app-private files).
  Delete: confirm dialog → `ContentResolver.delete` (all-files mode) / `DocumentsContract.deleteDocument` (SAF) → remove `file_meta` row.
  File info: name, path/location, size, modified, pages (PDF).
- **FR-019** Selection mode: long-press or select icon; top bar shows count, Share (multiple URIs, `ACTION_SEND_MULTIPLE`), Delete.
- **FR-020** `SampleFiles`: `assets/samples/sample.pdf|.docx|.xlsx|.pptx` copied to `filesDir/samples/` on first run,
  name "Sample File", date shown 01/01/2023; shown only when a tab×chip list is otherwise empty; cannot be deleted.
- **FR-021** Default opener. `MainActivity` intent-filters: `ACTION_VIEW` + `CATEGORY_DEFAULT`, schemes `content` and `file`,
  MIME types `application/pdf`, `application/msword`,
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/vnd.ms-excel`,
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/vnd.ms-powerpoint`,
  `application/vnd.openxmlformats-officedocument.presentationml.presentation`. Incoming URI opens the matching viewer
  (legacy `.doc/.xls/.ppt` still hit FR-036) and is added to Recent; persist the read grant when offered.
  Settings row **Set as default** opens `Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` for this package (API 31+;
  on older APIs open the app details screen). Android has no document-role API; "Always" in the system Open-with sheet
  is what stores the default.

## 4. Reader

- **FR-030** PDF viewer ports: ps:`core/pdf/src/main/kotlin/.../render/PdfRenderSession.kt`,
  ps:`core/data/.../pdf/PdfAccess.kt` (probe, open, password → ps:`core/pdf/.../PdfDecryptor.kt` to a cache copy),
  ps:`feature/reader/.../reader/PdfPages.kt` (continuous list, pinch zoom 1–5×, double-tap), page chip "n / N".
  Bitmaps sized to viewport width × zoom, LRU of ±3 pages.
- **FR-031** Search: ps:`core/pdf/.../text/PdfTextIndex.kt` built once in background via PdfBox; hit rects from
  ps:`core/pdf/.../text/PdfWords.kt`; highlight hits, next/prev, count.
- **FR-032** Highlight: ps:`feature/reader/.../markup/MarkupLayer.kt` + `MarkupViewModel.kt` for word selection;
  write with ps:`core/pdf/.../PdfMarkupWriter.kt` (real `/Highlight` annotations). Save to temp then replace original when
  writable; otherwise save `<name>_highlighted.pdf` beside/output folder.
- **FR-033/034/035** Office viewer: `office/` converts OOXML to one HTML file in cache using `ZipFile` + `XmlPullParser`
  (pattern: ps:`core/epub/.../XmlDocuments.kt`, ps:`feature/books/.../BookWebView.kt`), shown in a WebView with JS off,
  `WebViewAssetLoader` for cached images, `findAllAsync` for search.
  DOCX: paragraphs, runs (b/i/u), Heading1–6, lists as bullets, tables, inline images. XLSX: sheet tabs, shared strings,
  inline strings, numbers as stored (no formula eval), max 5,000 rows × 100 cols per sheet. PPTX: one card per slide with
  text frames in order and pictures. No fidelity guarantee (stated in UI "Simplified view").
- **FR-036** `.doc/.xls/.ppt`: open → message "This older format opens in another app" + `ACTION_VIEW` chooser.
  *Limit: no pure-Java/Android renderer for these without Apache POI's size and Android incompatibilities; out of v1.*
- **FR-037** Reader top bar: back, name, search, share, star, overflow (AI Translate, AI Summary, Edit/Sign [Pro crown]).

## 5. Create PDF: scan + images

Reuse pdfscanner's capture design; port only Document mode.

| Port (ps: path) | Into | Note |
|---|---|---|
| `third-party/opencv/` | `:third-party:opencv` | verbatim, generated |
| `core/imaging/.../DocumentDetector.kt`, `DocAlignerCorners.kt`, `PerspectiveWarper.kt`, `PageFilters.kt`, `PageProcessor.kt`, `QuadGeometry.kt`, `QuadEditing.kt`, `QuadSmoother.kt`, `StabilityTracker.kt`, `SkewEstimator.kt`, `ImageFormat.kt` + `core/imaging/src/main/assets/docaligner_lcnet100.tflite` (2.3 MB) + `NOTICE_DOCALIGNER.txt` | `imaging/` | drop `PerceptualHash`, `ProPageFilters` |
| `core/model/.../DocumentTypes.kt` (Quad, NormalizedPoint, PageFilter, PageRotation…), `ScanQuality.kt`, `FittedRect.kt`, `PdfLayout.kt` | `imaging/model/` | pure Kotlin |
| `feature/capture/.../camera/CameraScreen.kt`, `CameraViewfinder.kt`, `CameraViewModel.kt`, `DocumentEdgeAnalyzer.kt`, `EdgeOutlineOverlay.kt`, `AutoCaptureController.kt`, `CaptureControls.kt`, `CaptureBars.kt`, `CapturedPages.kt`, `CaptureSession.kt`, `PageImporter.kt`, `CameraPermissionStatus.kt`, `CameraXExtensions.kt` | `ui/scan/` | Document mode only |
| `feature/capture/.../review/PageReviewScreen.kt`, `PageReviewViewModel.kt`, `CropEditor.kt`, `ReviewPageStrip.kt`, `ApplyEdgesReveal.kt` | `ui/scan/review/` | |
| `core/data/.../session/ScanSessionStore.kt` | `ui/scan/` | plain JSON in `noBackupFilesDir/scan/`, no encryption |
| `core/pdf/.../PdfWriter.kt`, `PdfOverlay.kt` | `pdf/` | |
| `core/data/.../tools/ImagePdfRepository.kt` (`imagesToPdf`, `PageMargin`) | `pdf/` | minus `ToolWorkspace`/`PdfAccess` coupling |

- **FR-040** `CreatePdfSheet` (S11).
- **FR-041** Image to PDF: `PickMultipleVisualMedia` (Photo Picker, no media permission), up to 100 images, reorder grid,
  page size A4 / Letter / Fit image, margin None/Small/Wide, EXIF rotation honoured, JPEG q85 max side 2,480 px.
- **FR-042** Camera: CameraX preview + capture + analysis; live quad (OpenCV on luma, ~15 fps, `QuadSmoother`), DocAligner on
  the captured still, auto-capture via `StabilityTracker`, flash, gallery import (`PageImporter`), shutter sound off by default.
  Keep the fixed bugs listed in ps:`memory.md` (Offset-based overlay drawing; auto-capture re-arm).
- **FR-043** Review: corner drag with snapping, Apply edges animation, rotate, filter (Original, Enhanced, Grey, B&W — first page
  starts Original per ps decision), reorder, delete with undo, add more pages.
- **FR-044** Save: `PageProcessor` per page → `PdfWriter.addJpegPage` → FR-045 output → open in reader; session deleted on save or
  on "Discard".
- **FR-045** `OutputFolder`: API 29+ `MediaStore.Files` insert under `Documents/PDF Reader/` (`IS_PENDING` until written);
  API 26–28 direct file. Names: `Scan yyyy-MM-dd HH.mm.ss.pdf`, `Images yyyy-MM-dd HH.mm.ss.pdf`, collisions get ` (n)`.

**Improvements over pdfscanner (only where it is weak for this app):**
1. `CameraViewModel.kt` is 1,159 lines serving 7 scan modes (Document, Photo, ID, Book, Signature, OCR, QR) — port Document only
   and split capture vs routing so the class stays < 400 lines. Drop `ModeAwareAnalyzer`, `BarcodeAnalyzer`, `ModeSheets`,
   `ModeGuideOverlay`, `PhotoSizeSheet`, `ScanModeImageOps`, `ScanModeInfoDialog`, `SpiritLevelOverlay`.
2. Encrypted vault (`EncryptedFileStore`, SQLCipher, `SecureKeys`, `DocumentRepository`) is unnecessary: output is a user-visible
   PDF. Write the PDF directly instead of library-save-then-export.
3. No path exists to sign an **existing** PDF: signatures are only placed on scanned image pages (`AnnotationEntity.pageId`) and
   drawn by `PdfWriter.drawOverlay`. Add `PdfSigner` (FR-053).
4. Two `SignaturePad.kt` files (ps:`core/ui/.../component/SignaturePad.kt` primitives + ps:`feature/document/.../sign/SignaturePad.kt`
   composable) → one `ui/sign/SignaturePad.kt`.
5. `TextPdf.kt` uses `PDType1Font.HELVETICA` (WinAnsi only) → cannot write Bengali/Hindi/CJK. AI output uses
   `android.graphics.pdf.PdfDocument` + `StaticLayout` instead (FR-066), which shapes any script with system fonts.

## 6. Signature & edit (Pro)

- **FR-050** `SignatureStore`: from ps:`core/data/.../sign/SignatureRepository.kt` minus DB/encryption — PNGs in
  `filesDir/signatures/<uuid>.png` (transparent), max 3 (`AppLimits.MAX_SIGNATURES`), list by `lastModified`, delete.
  `SignaturePad` (Undo/Clear/Save, fixed dark ink on paper background both themes, `renderSignature` crop) from the two ps pad files.
- **FR-051** Import from photo: Photo Picker → luma → `inkArgb` / `cropToInk` from
  ps:`feature/capture/.../camera/SignatureInk.kt` → transparent PNG → store.
- **FR-052** `PlaceOnPdfScreen`: render page via `PdfRenderSession`; overlay signature/text boxes stored as page fractions;
  drag/pinch/rotate via ps:`core/data/.../sign/AnnotationRepository.kt` `AnnotationBounds.applyGesture` (min 0.05, max 1.5);
  text/date stamp dialog from ps:`feature/document/.../sign/StampDialog.kt`; multiple pages; delete selected.
- **FR-053** `PdfSigner.sign(input, password, overlays: Map<pageIndex, List<PdfOverlay>>, target)`: PdfBox load with
  `MemoryUsageSetting.setupMixed(16 MB)`, per page `PDPageContentStream(doc, page, AppendMode.APPEND, true, true)`
  (pattern: ps:`core/pdf/.../PdfWatermarker.kt`), placement math from `PdfWriter.drawOverlay` mapped to the page
  `cropBox` and `/Rotate`; save `<name>_signed.pdf` to `OutputFolder`; original untouched.

## 7. On-device AI

No inference ever leaves the phone. No runtime API calls. Engines sit behind `Translator` / `SummaryEngine` interfaces.

**FR-063 Page text** (`ai/PageTextExtractor`): PdfBox `PDFTextStripper` per selected page. If a page yields < 20 non-space chars
it is treated as scanned: render at 1,600 px width (`PdfRenderSession`) → ML Kit Latin text recognition (bundled model;
port ps:`core/ocr/.../TextRecogniser.kt`). *Limit: OCR covers Latin script only in v1; non-Latin scanned pages report
"No readable text on this page".* AI v1 accepts PDFs only (as in S04); DOCX is a later addition.

**Translate — ML Kit on-device Translation** (FR-064, FR-065)
- `com.google.mlkit:translate` + `com.google.mlkit:language-id` (language-ID model is bundled in the library).
- Targets, and only these: Italian `it`, Polish `pl`, Portuguese `pt`, Dutch `nl`, French `fr`, German `de`,
  Spanish `es`. French, German and Spanish are the three extra European languages. English is the ML Kit pivot, not a row
  in the sheet. ~30 MB per language. Nothing is downloaded until the user picks that language.
- Model delivery: `translator.downloadModelIfNeeded(DownloadConditions)` — one-time file from Google's ML Kit host, then
  offline. Document text is never sent. ML Kit cannot load these models from app assets or PAD. If that host is refused,
  translation is out of v1 (no custom engine).
- Source language: LanguageIdentification on the first 1,000 chars; `und` → user chooses. Target default: English.
  The sheet shows the seven names, a downloaded mark, and the size on rows that are not downloaded yet. Picking one that
  is missing starts the download (Wi-Fi-only by default, "Use mobile data" offered) and then translates. Cancel leaves
  the language unselected.
- Limits (shown in the "?" help): sentence-level quality, pairs pivot through English inside ML Kit, layout and tables
  become plain paragraphs. One "Portuguese" row. Scanned pages still depend on Latin OCR (FR-063). Paragraph by
  paragraph, sequentially.

**Summary — 2 or 3 paragraphs** (FR-068, FR-069). Hallucination is accepted. No accuracy target. FR-070 is dropped.
- Button stays **AI Summary** (S03). Input is the selected page text from FR-063 (free: 1 page; Pro: up to 10, truncated at
  2,500 words so prompt + output fit the model's 4,096-token context). Output is 2 or 3 paragraphs, one inference, no map-reduce.
  ChatML prompt tells the model to write exactly three paragraphs of prose, no lists. `n_ctx` 4096, max 400 new tokens,
  temperature 0.4, repeat penalty 1.2. Stream tokens; cancel stops the decode loop.
- Paragraph clamp (the model does not obey counts: 2–11 paragraphs seen in testing): split on blank lines, drop exact repeats,
  keep the first 3; if only 1 remains, split it at the sentence boundary nearest its middle.
- Model: **Minueza-2-96M-Instruct-Variant-04, GGUF Q2_K** — `Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf`,
  **65,518,432 bytes (~62.5 MiB)**, sha256 `3e6131f72e6795981798b91ed11eb118d1b830a2fc5a159723aecca498ed8d39`, from
  `https://huggingface.co/mradermacher/Minueza-2-96M-Instruct-Variant-04-GGUF` (base `Felladrin/Minueza-2-96M-Instruct-Variant-04`),
  Apache-2.0. Run with llama.cpp on CPU. It is the smallest downloadable file found that writes multi-paragraph prose on a
  page prompt; content is loosely on-topic and often invented, which the client accepts. Nothing real under 60 MB qualifies:
  Minueza-32M-UltraChat exists only as F16 (66,322,112 bytes; hidden size 312 cannot be quantized by llama.cpp), and SmolLM2
  135M starts at 88,202,080 bytes (Q2_K). Rejected: Gemma 3 270M (smallest file 180,104,224 bytes, UD-IQ2_XXS GGUF; smallest
  `.task` 249,233,408 bytes), SmolLM-135M `.task` (166,754,726 bytes), Qwen2.5 0.5B (≥ 513 MB). *Upgrade if the client wants
  output closer to the page:* SmolLM2-135M-Instruct Q4_0 (`bartowski/SmolLM2-135M-Instruct-GGUF`, 91,893,088 bytes,
  Apache-2.0), same runtime.
- Runtime: llama.cpp, not MediaPipe. Loaded code for `libllama` + `libggml-base` + `libggml` + one `libggml-cpu` variant is
  ~6 MB once stripped (measured on the `b11259` Android arm64 build), so it fits in the base. MediaPipe `tasks-genai` 0.10.35
  adds a 26.6 MB arm64 `.so` and its smallest LLM `.task` files are larger, so it is not used.
- Delivery: the model ships **with the app**. `:ai_summary_model` is an **install-time** asset pack (`deliveryType = "install-time"`), so Play downloads it as part of the install. No in-app download card, no `fetch`, no Wi-Fi confirmation. After install, `getPackLocation("ai_summary_model").assetsPath()/Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf` is present. Summary works in airplane mode on first launch. Local testing: `bundletool --local-testing`. The store download is the rest of the app plus this 65.5 MB file (it barely compresses), so the install is over the old 60 MB cap.
- Device gate (`AiLimits`): `arm64-v8a`, RAM ≥ 2 GB (measured peak RSS 231 MB at 4,096 context on desktop CPU). Otherwise "AI Summary isn't supported on this device" (Translate still works). CPU only.
- Language: the model writes the paragraphs itself. No ML Kit pivot (FR-070 dropped). Non-English pages may come back in English
  or mixed; that is accepted.
- Limits (**FR-071**): Summary free = 1 page, Pro = 10 pages, output always 2 or 3 paragraphs. Translate free = 1, Pro = 50.

**FR-066 Result screen:** selectable text, "AI-generated — may be inaccurate" label, Copy, Share (text), Save as PDF
(`TextToPdf` with `PdfDocument` + `StaticLayout`, A4, 11 sp) to `OutputFolder`, and **Report** (mail intent with a template;
no content attached automatically) for Play's AI-generated content policy.
**FR-067** Quit dialog on back from Select page / processing (S05); Quit cancels jobs and discards output.
**FR-060/061/062** FAB + dialog (S03), PDF-only file chooser reusing the library list, Select page grid (S01) with PdfRenderer
thumbnails (~240 px), selection counter, free/Pro limit, disabled Select until ≥ 1.

## 8. Settings, Pro, ads

- **FR-080** Settings screen (S06) + "Set as default" row (FR-021) + conditional "Privacy options" row (`AdConsent.privacyOptionsRequired()`).
- **FR-081** Rate Us: Play In-App Review; fallback `market://details?id=`. No rating gating or incentives.
- **FR-082** Share: `ACTION_SEND` text with the Play Store URL.
- **FR-083** Privacy policy: `ACTION_VIEW` to the client-hosted URL (same URL as Play Console).
- **FR-084** Billing: port ps:`core/billing/.../BillingManager.kt`, `Products.kt`, `Store.kt`, `SubscriptionOffers.kt`.
  One subscription `pdf_pro`, base plans `monthly` and `yearly`; free-trial offer on `yearly` configured in Play Console.
  Entitlement cached in DataStore (offline), refreshed on resume via `queryPurchasesAsync`, acknowledge purchases.
- **FR-085** Paywall: plan cards from `ProductDetails` (formatted price, period), trial text built from
  `SubscriptionOffers.trialPeriod` ("7-day free trial, then ₹X/year, renews automatically, cancel anytime in Google Play"),
  Restore, Manage subscription link, Terms + Privacy links. The "Free Trial" CTA reads "Get PDF Pro" when no trial offer is
  returned for the user.
- **FR-086** Pro gating: `Entitlement.isPro` flow → hide ads + crowns, lift AI limits, unlock Edit (FR-050–053).
- **FR-087** UMP: port ps:`core/ads/.../AdConsent.kt`; consent requested at launch before any ad request.
- **FR-088** Ads init: remove `MobileAdsInitProvider` (ps:`app/src/main/AndroidManifest.xml`), call `MobileAds.initialize` only
  after consent `canRequestAds()` and only for non-Pro. Debug uses Google test IDs (ps:`core/ads/.../AdManager.kt` `AdUnits`);
  release IDs from `gradle.properties` → `BuildConfig`. `RequestConfiguration`: max rating PG, not child-directed.
- **FR-089** `BannerAd`: anchored adaptive banner (port ps:`feature/home/.../ads/AdBanner.kt`), placed under the bottom bar on
  the 4 tabs and under Search results; zero height until loaded and on failure (S12). One `AdView` kept alive across tab switches.
- **FR-090** `NativeAdSlot` on Select page: small native template (icon, headline, CTA) with "Ad" badge, grey skeleton while
  loading (S01), collapses on failure.
  No other formats (no app-open, interstitial, rewarded).

## 9. Play compliance

**Permissions (FR-100)**

| Permission | Use | Note |
|---|---|---|
| `CAMERA` | Scan | runtime, requested on first scan |
| `MANAGE_EXTERNAL_STORAGE` | List/delete all documents on API 30+ | Needs Play Console "All files access" declaration (document management core use). Behind `ALL_FILES_ACCESS` flag; SAF fallback ready (FR-101) |
| `READ_EXTERNAL_STORAGE` maxSdk 29, `WRITE_EXTERNAL_STORAGE` maxSdk 29 | API 26–29 listing/saving | |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Ads, billing, one-time model downloads | |
| `com.google.android.gms.permission.AD_ID` | AdMob | declare in Console |
| none for photos | Photo Picker | Photos & videos permission policy satisfied |
| `POST_NOTIFICATIONS` | not requested | no notifications, no foreground service |
Remove SDK-merged `FOREGROUND_SERVICE`, `ACCESS_ADSERVICES_TOPICS`, `ACCESS_ADSERVICES_ATTRIBUTION` as ps manifest does.
`allowBackup=false` (signatures and cached copies stay on device).

- **FR-101** All files access: explainer screen before the system settings page; app fully usable (SAF mode) if refused. If Play rejects
  the declaration, ship with `ALL_FILES_ACCESS=false` — no code change.
- **FR-102** Data safety: *Files and docs — not collected* (processed on device only). *Device or other IDs, app interactions,
  diagnostics — collected by AdMob for advertising/analytics, shared with Google.* Purchase history via Google Play Billing.
  ML Kit translation and llama.cpp summary run on-device. ML Kit usage telemetry disabled by removing
  `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` (as ps manifest line 90–92).
  Privacy policy names the translation model download (ML Kit host). The summary model is part of the install, not a separate download. No document text is sent.
- **FR-103** Subscriptions: price, period, trial length, renewal and cancel path visible on paywall before purchase; no dark patterns
  (close button visible immediately, no pre-selected upsell trickery); "Free Trial" wording only when a trial offer exists.
- **FR-104** Notices: Apache-2.0 notice for the Minueza-2-96M summary model in Settings; OSS licences
  (PdfBox, OpenCV, DocAligner, llama.cpp MIT, Minueza-2-96M Apache-2.0, ML Kit terms) reachable from Settings.
- **FR-105** Release checks: target SDK 37, 16 KB page-size alignment of all `.so` (OpenCV, LiteRT, llama.cpp), R8 build smoke test of
  PDF/OCR/scan/AI paths, offline QA (airplane mode) of reader/scan/sign/AI, no ads for Pro, target audience 13+ (not Families),
  AdMob placement: banner separated from the bottom bar by a divider to avoid accidental clicks.
