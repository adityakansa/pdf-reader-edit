# Work done

History and current state. Update this file with every FR you complete (date, FR IDs, what changed, how verified, what was not verified).

## Step 1 — build setup, theme, shell, library (2026-09-30)

FRs touched: FR-001, FR-002, FR-003, FR-004, FR-010, FR-011, FR-012, FR-013, FR-014, FR-015, FR-016,
FR-017, FR-018, FR-019, FR-020 (code, assets pending), FR-040 (sheet), FR-080, FR-081, FR-082, FR-083,
FR-084, FR-085, FR-086 (plumbing), FR-087, FR-088, FR-089, FR-100, FR-101, FR-104.

Build: `gradle/libs.versions.toml` rewritten with the TECH_SPEC §1 set; `:app` now applies Compose, KSP,
Hilt, Room and serialization on top of AGP 9's built-in Kotlin; minSdk 26, Java 17, R8 on for release with
keep rules in `app/src/main/keepRules/rules.keep` (AGP 9 convention — `proguardFiles` is not used).
`gradle.properties` carries `ALL_FILES_ACCESS`, the release ad unit ids and the privacy policy URL.

FR-101 without a second manifest: `tools:node` rejects manifest placeholders, so the permission *name* is
the placeholder instead — `${manageStoragePermission}` resolves to `MANAGE_EXTERNAL_STORAGE` or, when the
flag is false, to `INTERNET`, which is already declared and merges away. One line of Gradle, no flavour.

Code added under `…/pdfreaderpdffileedit/`: `App`, `MainActivity` (AppCompat + Compose + splash + the
FR-021 VIEW intent filters), `di/AppModule`, `ui/theme/*`, `ui/components/*` (TypeBadge, TypeChips,
FileRow, Intents), `ui/shell/*` (Routes, TopBar, BottomBar, AppNavHost, ShellViewModel), `ui/home/*`
(HomeScreen + tabs, sheets, storage explainer), `ui/search/*`, `ui/settings/*` (S06 layout, Rate Us,
Share, Privacy, Notices), `ui/paywall/*`, `ui/ai/AiAssistantDialog` (S03), `data/files/*` (DocFile,
FileIndex, FileRepository, Room `file_meta`, StorageAccess, OutputFolder, SampleFiles, IncomingFile),
`data/prefs/AppPreferences`, `ads/*` (UMP consent + adaptive banner), `billing/*` (BillingManager,
Entitlement).

Verified: `./gradlew :app:assembleDebug` succeeds. Nothing was run on a device.

Not done yet in this step: sample assets (`assets/samples/*`) are not in the repo, so FR-020 shows nothing
until they are added; the brand launcher icon is still the template (FR-006); reader, scan, sign and the
AI screens are not written yet, so their nav destinations are absent from `AppNavHost`.

## Step 5 — on-device AI: translate, summary engine, native build (2026-09-29)

FRs touched: FR-060, FR-061, FR-062, FR-063, FR-064, FR-065, FR-066, FR-067, FR-068, FR-069, FR-071.

Already in the repo before this step (written earlier but not recorded here): `ai/` (`PageTextExtractor`,
`Translator`, `AiLimits`, `AiResultHolder`, `SummaryEngine`), `ocr/TextRecogniser`, `pdf/TextToPdf`, and
`ui/ai/*` (assistant dialog, PDF-only picker, Select page with native ad, run screen with language sheet,
download dialog and Quit dialog, result screen with Copy/Share/Save as PDF/Report).

Changed in this step:
- llama.cpp is now a git submodule at `app/src/main/cpp/llama.cpp`, pinned to tag `b11259`
  (`d280808f5`). `app/src/main/cpp/CMakeLists.txt` builds it CPU-only (no OpenMP, no common/tools/tests,
  shared libs, 16 KB page alignment), plus `llama_jni.cpp`: load model → tokenize the ChatML prompt → decode →
  sample (repeat penalty over 64 tokens, top-k 40, top-p 0.95, temp 0.4) → stream each piece to Kotlin as raw
  bytes. Kotlin returns false from the sink to cancel, so the loop stops at the next token.
- `app/build.gradle.kts` turns on CMake only when the submodule is checked out (arm64-v8a only). Without
  it the app still builds and Summary shows "isn't supported". `libc++_shared.so` is `pickFirsts` because OpenCV
  ships it too.
- FR-068: a new `:ai_summary_model` module (`com.android.asset-pack`, `install-time`) with
  `assetPacks += ":ai_summary_model"` and `noCompress "gguf"`. The `.gguf` is git-ignored;
  `scripts/fetch-summary-model.sh` downloads it and checks its size and sha256.
- `SummaryModelDelivery` was looking in folders an install-time pack never uses. Install-time packs are
  merged into the app's assets, so it now copies the asset once to `noBackupFilesDir/models/` (through a `.part`
  file, size-checked) and gives llama.cpp that path.
- `SummaryEngine.summarise` is now a `channelFlow` of `SummaryUpdate(text, finished)`. The run screen shows
  the text as it is written, and the last update is the 2–3 paragraph clamp. The clamp and the UTF-8 handling
  (a token can end partway through a character) moved to the pure `ai/SummaryParagraphs.kt`.
- FR-065: when ML Kit says "und", the run stops and asks "Which language is this page in?" (all ML Kit
  languages) instead of guessing English. Source == target skips translation. Cancelling (Quit) no longer shows
  an error toast. A failed job says "Something went wrong" instead of "file could not be opened".
- `SummaryParagraphsTest` no longer subclasses the abstract `android.content.Context`, which could not
  compile; it tests `SummaryParagraphs` directly and has two new UTF-8 cases.

Verified: `llama_jni.cpp` compiles and links with `-Wall -Wextra -Wl,--no-undefined` against a host
(x86-64 Linux) build of llama.cpp b11259, and exports the expected `Java_…_LlamaBridge_nativeGenerate` symbol.

Not verified: this container's network policy blocks `dl.google.com` (Google Maven and the Android SDK) and
`huggingface.co`. So no Gradle/Android build ran for this step, the NDK build of llama.cpp did not run, the model
was not downloaded, and no summary was generated. The JVM run of `SummaryParagraphsTest` was stopped
before it finished (Maven Central rate-limited it). Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`
locally after `git submodule update --init`.

## Step 6 — sample files, Office viewer fixes (2026-09-29)

FRs touched: FR-020, FR-033, FR-034.

- `app/src/main/assets/samples/sample.{pdf,docx,xlsx,pptx}` added (9–40 KB each), so the existing
  `SampleFiles` now has something to show. They are built by `scripts/make_samples.py` (reportlab,
  python-docx, openpyxl, python-pptx, Pillow), so they can be rebuilt instead of edited by hand. Each one uses what
  its viewer supports: the PDF has a real text layer on two pages (search, highlight, AI), the DOCX has
  headings, bold/italic/underline, bullets, a table and a picture, the XLSX has two sheets with inline strings and
  numbers, and the PPTX has four slides with text and a picture.
- FR-034 bug fixed in `XlsxToHtml`: cells were placed in the order they appear in the XML. Spreadsheet
  files leave blank cells out, so every value after a gap shifted left. An inline rich-text cell (one `<t>` per
  run) also became several columns. Cells now go to the column in their `r` reference (padded with empty
  `<td>`s) and their text is collected until `</c>`. `XlsxColumnTest` covers the reference parsing.
- FR-033 bug fixed in `DocxToHtml`: paragraphs styled `List Bullet`/`List Number` (Word's built-in list
  styles, which have no `numPr`) now render as list items.

Verified: the four files open with the Python libraries that wrote them. Their zip parts match what the
converters read (`word/media/*`, `xl/worksheets/sheet{1,2}.xml` with `t="inlineStr"`, `ppt/slides/slide1–4.xml`,
`ppt/media/*`). Not verified: no Android build or device run (same network block as Step 5), so the
viewers were not run on these files and `XlsxColumnTest` has not been run.

## Step 7 — brand icon and splash (2026-09-29)

FRs touched: FR-006.

- The template Android icon is gone. The adaptive icon is a brand-red field (`#D32F2F`, with a
  darker `#B71C1C` band) behind a white page with a folded corner, a red "PDF" band, two text lines and a pen,
  all inside the 66 dp safe zone. `ic_launcher_monochrome.xml` is a one-colour silhouette for Android 13 themed
  icons. The ten template `.webp` mipmaps were deleted: minSdk is 26, so `mipmap-anydpi-v26` always applies.
- Splash: the same foreground on a brand-red icon disc (`windowSplashScreenIconBackgroundColor`); a white page
  on the `#F6F6F6` splash background would otherwise be invisible.
- App label is now "PDF Reader & PDF File Edit" (26 characters) as FR-006 asks.

Verified: the vector paths were rasterised with cairosvg (circle mask, and the monochrome layer on a tinted
background) and checked by eye. Not verified: no Android build, so launcher masks on real devices were not
seen. FR-006 also says the icon must match the Play listing icon; a 512 px store icon still has to be exported
from these vectors when the listing is made.

## Step 8 — drag-to-reorder, apply-edges reveal, cleanup (2026-09-29)

FRs touched: FR-041, FR-043, FR-050.

- New `ui/components/DragReorder.kt`: one long-press-then-drag state for a `LazyVerticalGrid` or a `LazyRow`.
  The container's `layoutInfo` is the hit test, the list is reordered live through `onMove` each time the finger
  crosses another item, the dragged item is lifted (scale, shadow, z-order) and stays under the finger, and dragging
  near an edge auto-scrolls. It replaces the "nudge one place later" arrow buttons, the known gap from Step 3.
- Image to PDF (FR-041): the grid uses it (`ImageToPdfViewModel.move(from, to)`), and each tile shows its page number.
- Scan review (FR-043): the page strip uses it (`PageReviewViewModel.move`), and the selected page stays selected
  while it moves. Leaving the crop editor crossfades (450 ms) into the warped page as a simple "Apply edges" reveal.
- `Routes.Signatures` removed: no screen used it. FR-050's signature list, pad, photo import and delete live
  on the place-on-PDF screen.

Not verified: no Android build or device run (same network block as Step 5). Drag hit-testing uses
`visibleItemsInfo` offsets shifted by `viewportStartOffset`. If the grid's horizontal content padding is not in the
item x offsets, the hit area is off by 12 dp. It needs checking by hand on a device.

## Step 9a — usability pass: system bars, library, reader, search (2026-09-29)

A screen-by-screen review of the code against the screenshots and against Adobe Acrobat Reader, Adobe Scan
and CamScanner. The full review is in `docs/UX_REVIEW.md` (Step 9c). Fixed in this part:

- **System bars (all screens).** Edge-to-edge is on (and forced from Android 15), but every top and bottom bar
  is a custom row, so nothing reserved room for the status bar or the gesture bar. Icons sat under the clock
  and the tabs under the gesture handle. `AppNavHost` now pads the whole host with `safeDrawingPadding()`
  once. That consumes the insets, so Material app bars do not pad twice, and the IME inset is included. The
  camera keeps a black backdrop.
- **Selection mode (FR-019).** The select icon turned on selection mode with 0 files selected, but the top bar
  only switched when the count was above 0. The user got checkboxes with no count, no close button and no way
  out, and Back did not leave either. The top bar now follows `selectionMode`: close, "Select files" / "n
  selected", Select all / Clear, and Share/Delete disabled at 0. Back leaves selection, and from another tab
  Back returns to Document before it exits the app.
- **Library states.** While the first scan runs, the list showed "No documents yet". `FileIndex.scanning`
  now drives a "Looking for documents on your phone…" spinner. Empty tabs show an icon, a title, one line of
  explanation and the next step (Document: Allow access / Scan document / Image to PDF).
- **File info (FR-018)** now shows the PDF page count (it always passed `null`).
- **Bottom bar.** Tabs were clickable boxes with no ripple and no semantics. They are now `selectable(role =
  Tab)`, so TalkBack reads "Recent, tab, 2 of 4, selected". The Create button has a ripple and a Button role.
- **PDF reader.** The password field showed the password in plain text; it is now masked with a show/hide
  toggle, a password keyboard, and Open disabled until something is typed. System Back closed the document
  even with search or highlight mode open; it now closes the mode first. Search gets the keyboard straight away,
  shows "No matches" or "3/12", and its arrows have labels and are disabled with no matches. Highlight mode
  shows "Drag over the text you want to highlight, then tap ✓ to save". Tapping the page chip opens "Go to
  page". A file that fails to open shows why and a Go back button, not bare text.
- **Office viewer.** Search shows the match count (`WebView.setFindListener`), labelled arrows, and Back
  closes search first.
- **Search screen.** The keyboard opens straight away, "No files named “x”" shows when nothing matches, and
  the ⋮ button (which opened the file) is hidden there.

Not verified: no Android build or device run (network block, see Step 5).

## Step 9b — usability pass: camera, scan review, sign; review document (2026-09-29)

- **Camera (FR-042).** Back and the close button deleted every captured page without asking. Both now ask
  "Discard scan?" with Discard / Keep and review. The auto-capture and flash toggles have text labels
  (Auto/Manual, Flash on/off) and state icons. A status pill says "Point the camera at a document" / "Hold
  steady…" / "Tap the button to capture" / "Capturing…". The shutter has a spoken label, a ring that fills with
  auto-capture progress, a haptic tick and a white blink. Gallery is "Import" with a label, and the check mark
  is a "Done (n)" button. The top and bottom bars have a scrim so the white controls stay readable over a white page.
- **Scan review (FR-043/044).** The tool icons have labels (Crop/Apply, Rotate, Delete, Add page; "+" was a text
  button). Discard now asks first, as the FR-044 AC requires.
- **Edit/Sign (FR-050–052).** The tools have labels. Deleting a saved signature was a 20 dp target that acted
  immediately; it is now 32 dp with a confirmation. A newly drawn or imported signature is placed on the
  current page straight away. A hint says what to do next ("Tap a signature below to place it…"). Back with unsaved
  placements asks "Discard your changes?".
- `docs/UX_REVIEW.md`: the libraries used per format, a comparison with Acrobat Reader / Adobe Scan /
  CamScanner, the 16 fixes from Steps 8–9, and the 12 gaps still open in recommended order.

Checks run (no compiler available): every `R.string`/`R.plurals` used in Kotlin exists in `strings.xml`,
`strings.xml` parses with no duplicate names, and braces/parentheses balance in every Kotlin file changed in this
session. Not verified: no Android build or device run.

## State of the repo (2026-09-29)

- Fresh Android Studio template (no Activity). Not a git repository yet.
- Package / applicationId `com.whats.web.scan.webscan.pdfreaderpdffileedit` (same publisher prefix as the sibling
  `/Users/aditya/AndroidStudioProjects/WebScan`, a single-module View-based app with Play Billing in `ProActivity.kt`).
- AGP 9.3.3, Gradle 9.5.0, compile/target SDK 37, minSdk 24 (spec raises to 26), Java 11, R8 disabled.
- Dependencies: appcompat 1.8.0, core-ktx 1.19.1, material 1.14.0, junit/espresso test deps.
- Manifest: bare `<application>` with template theme and launcher icon; no Activity, no permissions.
- Source: only `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt`. **Nothing is stubbed and nothing is real — every FR is open.**

## Step 2 — reader (2026-09-30)

FRs touched: FR-030, FR-031, FR-032, FR-033, FR-034, FR-035, FR-036, FR-037.

`pdf/`: `PdfRenderSession` and `text/PdfWords.kt` are near-verbatim ports of pdfscanner's; `text/PdfTextIndex`
and `PdfMarkupWriter` are ports with the note/sticky-note half dropped (this app only highlights).
`PdfAccess` is new and replaces pdfscanner's vault-coupled one: it turns a content URI into a descriptor
for the platform renderer, an `InputStream` for PdfBox, a cache copy when PdfBox needs a real `File`, and
a decrypted copy when the file is password protected (the platform renderer reports that as a bare
`SecurityException`, which is the only signal there is).

`ui/reader/`: `PdfPages` is the continuous reader ported from pdfscanner minus its paged and night modes;
`PdfReaderScreen` adds the FR-037 bar (search with next/prev and a count, share, star, overflow with AI
Translate / AI Summary / Highlight / Edit-Sign behind the crown). Highlighting drags a box over a page,
resolves the words under it with `PdfWords`, and writes real `/Highlight` annotations to
`<name>_highlighted.pdf` in the output folder — the original is never rewritten, which is simpler than the
"replace when writable" rule in the spec and never risks someone's file.

`office/`: `OoxmlZip` reads the archive once into memory (capped at 64 MB) and hands parts to
`DocxToHtml` / `XlsxToHtml` / `PptxToHtml`, which build one HTML page each with the platform pull parser.
`OfficeReaderScreen` shows it in a WebView with JavaScript off, `findAllAsync` for in-document search and
the "Simplified view" label. Legacy `.doc/.xls/.ppt` short-circuit to the FR-036 message + chooser.

Verified: `./gradlew :app:assembleDebug` succeeds. No device run, so page rendering, the DOCX/XLSX/PPTX
conversions and the highlight round-trip are unverified against real files.

## Step 3 — create PDF: images and scanning (2026-09-30)

FRs touched: FR-040, FR-041, FR-042, FR-043, FR-044, FR-045.

`:third-party:opencv` copied verbatim from pdfscanner (17 MB of generated sources and `.so`s) and added to
`settings.gradle.kts`; `:app` gained LiteRT, CameraX (incl. `camera-view`) and `abiFilters arm64-v8a,
armeabi-v7a` on `defaultConfig` so debug APKs carry no x86 either. The DocAligner model and its notice are
in `app/src/main/assets/`.

`imaging/` is the pdfscanner port: `DocumentDetector`, `DocAlignerCorners`, `PerspectiveWarper`,
`PageFilters` (the three Pro-only filters removed), `PageProcessor`, `QuadGeometry`, `QuadEditing`,
`QuadSmoother`, `StabilityTracker`, `SkewEstimator`, `ImageFormat`. `imaging/model/ScanTypes.kt` is a
trimmed `core/model`: four filters, three page sizes, `Quad`/`NormalizedPoint` (now `@Serializable` so a
session can be written to disk), `PdfLayout`, `FittedRect`. `PerceptualHash` and `ProPageFilters` dropped.

`pdf/`: `PdfWriter` + `PdfOverlay` ported minus encryption and the invisible OCR text layer;
`drawOverlay` is shared with the signer. `ImagesToPdf` is new (FR-041): sampled decode to 2,480 px,
EXIF-upright, JPEG 85, up to 100 images.

`ui/scan/` is written fresh rather than ported — pdfscanner's `CameraViewModel` is 1,159 lines for seven
modes. `CameraViewModel` is ~150: analysis frames → `DocumentDetector` → `QuadSmoother` →
`StabilityTracker` → auto-capture, plus flash, gallery import and the session count. `CameraScreen` binds
CameraX and draws the live outline. `ScanSessionStore` keeps pages and their crop/filter/rotation as JSON
in `noBackupFilesDir/scan`. `ui/scan/review/` has the crop editor (corner drag with snapping to the
detected quad, refusing concave quads), rotate, the four filters, reorder, delete with an Undo snackbar,
and Save, which runs `PageProcessor` per page into `PdfWriter` and out through `OutputFolder`.

A written PDF is handed to `IncomingFile` — the same path an "Open with" document takes — so the shell
opens it in the reader without waiting for MediaStore to index it. `IncomingFile` is now a `Channel`, not
a replaying `SharedFlow`, so a rotation cannot reopen the last document.

Verified: `./gradlew :app:assembleDebug` succeeds (112 MB debug APK — both ABIs, OpenCV and the 2.3 MB
DocAligner model; a release bundle splits by ABI). Nothing run on a device: detection, auto-capture, the
crop editor and PDF output are all unverified in practice.

Known gaps in this step: reordering is "nudge one place later", not drag-and-drop (FR-041/FR-043 AC says
drag); there is no "Apply edges" reveal animation; the review screen has no per-page "add more pages"
return other than the + button popping back to the camera.

## Step 4 — signature and edit (2026-09-30)

FRs touched: FR-050, FR-051, FR-052, FR-053.

`sign/SignatureStore` keeps at most three transparent PNGs in `filesDir/signatures` (no database, no
encryption — `allowBackup=false` already keeps them on the phone). `sign/SignatureInk` is the pdfscanner
ink lift, wrapped in an `extract()` that picks the paper level from the 90th-percentile luma and returns
null when there is too little ink, which is FR-051's "No signature found". `sign/Placement` +
`PlacementBounds` is pdfscanner's `AnnotationBounds` maths without the database entity.

`ui/sign/SignaturePad.kt` is the single pad the spec asks for (pdfscanner has two): undo, clear, and a
save that renders the strokes cropped to the ink on transparency, with ink and paper colours fixed in
both themes so a signature is never written white-on-white into someone's contract.
`PlaceOnPdfScreen` renders the page through `PdfRenderSession`, places signatures and text/date stamps as
page fractions, and drives them with `detectTransformGestures` (drag, pinch 5–150 %, rotate).

`pdf/PdfSigner` is new — pdfscanner has no path to sign an *existing* PDF. Each page is reopened with
`AppendMode.APPEND`, so existing text and its selectability are untouched, and placements are mapped onto
the page's `cropBox` and `/Rotate` before `drawOverlay` (shared with `PdfWriter`) draws them. Output is
`<name>_signed.pdf` through `OutputFolder`; the original is never rewritten.

Verified: `./gradlew :app:compileDebugKotlin` succeeds. Not verified: the FR-053 AC's "within 1 pt"
placement check needs a device or instrumented test and has not been run — in particular the `/Rotate`
90/270 mapping is reasoned, not measured.

## Documentation pass (2026-09-29) — no app code written, nothing committed

Inputs: the client's Play Store copy (in `BRD.md` §5), 13 client screenshots, this repo, and pdfscanner.

| File | Content |
|---|---|
| `docs/BRD.md` | Why/what: users, goals, scope, store-copy trace, R1–R8 requirements, exact ad placements, NFRs, assumptions |
| `docs/TECH_SPEC.md` | How: stack, package layout, per-FR design, pdfscanner port map + improvements, on-device AI, Play compliance |
| `docs/FR_CHECKLIST.md` | 65 FRs (FR-001…FR-105) with testable AC, sources, files, deps, Play notes, status (0 done) |
| `docs/architecture.md` | Current template structure, target module/package diagram, scan/signature/AI data flows |
| `docs/workdone.md` | This file |
| `docs/screenshots/S01…S13-*.jpg` | 13 client screenshots (one file each). Names are listed at the top of `BRD.md`. |
| `graphify-out/` | Code graph of the current codebase (below) |

### Code graph

Built with the graphify skill (`/Users/aditya/.claude/skills/graphify/SKILL.md`, package `graphifyy`, interpreter in
`graphify-out/.graphify_python`) **before** the docs were written, so it is the pre-implementation baseline.

- Outputs: `graphify-out/graph.json`, `graphify-out/graph.html`, `graphify-out/GRAPH_REPORT.md`, `manifest.json` (for incremental
  updates), `cache/`, `cost.json`.
- Result: 28 nodes, 24 edges, 7 communities — Template Launcher Icons, Instrumented Test Stub, Unit Test Stub, Gradle Wrapper
  Script, App Module Build, Root Build Script, Settings Module Include. 100 % EXTRACTED, 0 tokens.
- Code extracted by AST; the 10 launcher-icon images were described inline (default template icon) instead of by subagents.
- Health check warning: 5 dangling-endpoint edges — imports in the two example tests pointing at external JUnit/AndroidX test
  symbols (`AndroidJUnit4`, `RunWith`, `Test`, `InstrumentationRegistry`). Expected, not corruption.
- After code changes: `graphify update .` (code only, no LLM). Full rebuild with docs/images: run the skill (`/graphify .`).
  Query: `graphify query "<question>"`, `graphify path "A" "B"`, `graphify explain "X"`.

### Reference project: pdfscanner (DocVault)

`/Users/aditya/StudioProjects/pdfscanner` — it is **not** under `AndroidStudioProjects`. Multi-module Compose + Hilt app,
package `com.nuvoralabs.docvault`, release 1.1. Read its `CLAUDE.md`, `architecture.md`, `memory.md`,
`docs/pdf-engine-decision.md` before porting. What this app reuses (exact list in TECH_SPEC §5, §6, §8):

- Scanner: `core/imaging/*` (DocAligner TFLite + OpenCV detector, warper, filters, quad editing, stability tracker),
  `feature/capture/.../camera/*` and `review/*` (Document mode), `core/data/.../session/ScanSessionStore.kt`,
  `third-party/opencv` (slim OpenCV 4.14, 9.4 MB/ABI).
- PDF: `core/pdf/.../PdfWriter.kt`, `PdfOverlay.kt`, `PdfMarkupWriter.kt`, `PdfDecryptor.kt`, `PdfWatermarker.kt` (append pattern),
  `render/PdfRenderSession.kt`, `text/PdfTextIndex.kt`, `text/PdfWords.kt`; `core/data/.../pdf/PdfAccess.kt`,
  `core/data/.../tools/ImagePdfRepository.kt`; reader UI `feature/reader/.../reader/PdfPages.kt`, `markup/MarkupLayer.kt`.
- Signature: `core/data/.../sign/SignatureRepository.kt`, `AnnotationRepository.kt` (`AnnotationBounds`),
  `core/ui/.../component/SignaturePad.kt`, `feature/document/.../sign/SignaturePad.kt`, `StampDialog.kt`,
  `feature/capture/.../camera/SignatureInk.kt`.
- OCR `core/ocr/.../TextRecogniser.kt`; ads `core/ads/.../AdConsent.kt`, `AdManager.kt`, `feature/home/.../ads/AdBanner.kt`;
  billing `core/billing/*`; manifest hygiene from `app/src/main/AndroidManifest.xml`.
- Improvements decided (TECH_SPEC §5): Document mode only (its `CameraViewModel.kt` is 1,159 lines for 7 modes); no encrypted
  vault; direct PDF output; new `PdfSigner` for existing PDFs (pdfscanner can only sign its own scanned pages); one SignaturePad;
  `PdfDocument` instead of Helvetica-only `TextPdf.kt` for AI output.
- Deliberate divergence: pdfscanner ships with **no storage permission**; this app needs device-wide listing (screenshots), so it
  uses All files access with a SAF fallback flag.

### Decisions and assumptions recorded

- On-device AI: ML Kit Translation (~30 MB per language, one-time download from Google's ML Kit host) + summary model
  Minueza-2-96M-Instruct-Variant-04 Q2_K GGUF (`Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf`, 65,518,432 bytes, Apache-2.0,
  `mradermacher/Minueza-2-96M-Instruct-Variant-04-GGUF`) run by llama.cpp (~6 MB stripped arm64 `.so`). Client (2026-09-29):
  ship this file with the app (install-time asset pack, no second download). The Play install is therefore over 60 MB.
  Smallest real file that wrote
  multi-paragraph prose in a local llama.cpp test; output is loosely on-topic and invented, which is accepted. Nothing
  downloadable under 60 MB qualified. Gemma 3 270M (≥ 180 MB), MediaPipe (26.6 MB `.so`) and Gemini Nano (device coverage)
  rejected. Upgrade path if closer-to-page text is wanted: SmolLM2-135M-Instruct Q4_0 (91,893,088 bytes). The app clamps output to
  2 or 3 paragraphs. FR-070 (translate-then-summarise) dropped.
- Screenshot readings: "Choose 1 page" = free-tier limit; sample rows = bundled samples for empty lists; list-with-pen icon =
  multi-select; ad ⓘ/"AI" = AdChoices; Select page ad = small native ad; "Edit" in Pro = signature + text stamp (highlight free);
  S04's two Portuguese entries collapse to one (ML Kit limit).
- Client decisions (2026-09-29): the library scans the whole phone for PDF/Word/Excel/PPT (MediaStore + a walk of public
  folders, FR-011) and lists them like S07–S10. The app registers as an opener for all seven document MIME types and
  Settings has "Set as default" (FR-021). Translation targets are only Italian, Polish, Portuguese, Dutch, French,
  German, Spanish (Arabic removed at client request); each ~30 MB model downloads only when the user picks it (FR-064/065). French/German/Spanish were chosen
  as the "2–3 more European" languages.

### Open questions / blockers for the client

1. UI languages to ship beyond English (FR-005).
2. Is a one-time model download from Google's ML Kit host acceptable for translation? (Otherwise translation needs a custom
   PAD-delivered engine, not in v1.)
3. All files access declaration may be rejected by Play; SAF fallback is specified (FR-101).
4. AdMob app/unit IDs, Play Console subscription setup (`pdf_pro`, trial length, prices), privacy policy URL, brand icon/font.
5. Legacy `.doc/.xls/.ppt` are hand-off only in v1.

### Not verified in this pass

- No build was run (no code changed). Dependency versions marked *pin at implementation* in TECH_SPEC were not resolved.
- Summary model was tested only on a Mac (llama.cpp `b11259`, CPU, ~350 tokens/s, 231 MB peak RSS). Speed and memory on a
  2 GB arm64 phone must be confirmed at FR-069. No accuracy measurement is required.
