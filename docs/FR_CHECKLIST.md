# FR checklist

Read docs/BRD.md, docs/TECH_SPEC.md, docs/architecture.md, and docs/workdone.md before implementing. Check off an FR only when acceptance criteria pass.

Format: `ID title` · **AC** acceptance criteria (all must pass) · **Src** BRD section / screenshot · **Files** under
`app/src/main/java/com/whats/web/scan/webscan/pdfreaderpdffileedit/` unless absolute · **Deps** FRs that must be done first ·
**Play** compliance note · **Status** against current code. `ps:` = `/Users/aditya/StudioProjects/pdfscanner/`.

Baseline (2026-09-29): the repo is an empty Android Studio template — no Activity, no source in `src/main`. **0 of 64 active FRs done.** FR-070 is dropped (summary does not pivot through translation).

## Shell / navigation

- [ ] **FR-001 Build setup** — Status: not done (compile/target SDK 37 already set; everything else missing)
  - AC: `./gradlew :app:assembleDebug :app:assembleRelease` succeed; minSdk 26; Compose, Hilt, KSP, Room, serialization plugins applied; all deps from TECH_SPEC §1 in `gradle/libs.versions.toml`; `:third-party:opencv` included and `org.opencv.core.Core` loads on device; release has R8 on and keep rules; APK contains only arm64-v8a/armeabi-v7a libs.
  - Src: TECH_SPEC §1, §3 · Files: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/keepRules/rules.keep`, `third-party/opencv/**` (copy of ps:`third-party/opencv`) · Deps: — · Play: 16 KB alignment verified in FR-105.
- [ ] **FR-002 Theme & tokens** — Status: not done (template purple MaterialComponents theme)
  - AC: colours/type/shapes from TECH_SPEC FR-002 defined once in `ui/theme`; no hard-coded colours in screens (grep for `Color(0x` outside `ui/theme` returns nothing); text contrast ≥ 4.5:1 for body text; light theme matches S07 visually.
  - Src: BRD R1, S07 · Files: `ui/theme/*`, `res/values/themes.xml`, `res/font/*` · Deps: FR-001.
- [ ] **FR-003 App shell & bottom bar** — Status: not done
  - AC: launcher opens `MainActivity` (AppCompatActivity, edge-to-edge); bottom bar Document/Recent/Create/Favourite/Setting; selected tab red icon+label+top bar indicator; Create opens Create PDF sheet; back from a tab root exits; state survives rotation and process death.
  - Src: BRD R1-1, R1-3, S07, S11 · Files: `App.kt`, `MainActivity.kt`, `ui/shell/*`, `AndroidManifest.xml` · Deps: FR-002.
- [ ] **FR-004 Top bar** — Status: not done
  - AC: search icon opens Search; crown visible only for non-Pro and opens Paywall; "PDF Reader" with "Reader" red; sort icon opens sort sheet; select icon toggles selection mode; all icons have content descriptions and 48 dp targets.
  - Src: BRD R1-2, S07 · Files: `ui/shell/TopBar.kt` · Deps: FR-003 (crown gating completes with FR-086).
- [ ] **FR-005 In-app language** — Status: not done
  - AC: Settings > Language lists locales from `locales_config.xml`, current one shown on the row; choosing one recreates UI in that language and persists across restarts; on Android 13+ the choice appears in system per-app language settings.
  - Src: BRD R7-2, S06 · Files: `ui/settings/*`, `res/xml/locales_config.xml`, `AndroidManifest.xml` · Deps: FR-003 · Note: UI languages beyond English are an open question.
- [ ] **FR-006 Brand icon & splash** — Status: not done (template Android icon, see graph community "Template Launcher Icons")
  - AC: adaptive + monochrome launcher icon replaces template in all densities; splash via core-splashscreen shows brand icon; app label "PDF Reader & PDF File Edit" (≤ 30 chars) in `strings.xml`.
  - Src: BRD §1 · Files: `res/mipmap-*`, `res/drawable/ic_launcher_*`, `res/values/strings.xml` · Deps: FR-001 · Play: icon must match store listing icon.

## File library

- [ ] **FR-010 Storage access** — Status: not done
  - AC: first launch shows explainer then (API 30+) system All-files-access page; returning with permission granted populates list; denied → SAF mode with "Add files"/"Add folder" actions; API 26–29 uses READ_EXTERNAL_STORAGE; `ALL_FILES_ACCESS=false` build never declares/requests MANAGE_EXTERNAL_STORAGE.
  - Src: BRD NFR-4, TECH_SPEC FR-010 · Files: `data/files/*`, `ui/home/StorageAccessScreen.kt`, `app/build.gradle.kts` (flag), manifest · Deps: FR-003 · Play: All files access declaration (FR-101).
- [ ] **FR-011 Document index** — Status: not done
  - AC: with permission, every pdf/doc/docx/xls/xlsx/csv/ppt/pptx on the phone's shared storage (Download, Documents, other public folders; not `Android/data`) appears under All and under its chip, like S07–S10, within 2 s for 1,000 files; a file MediaStore did not index but that sits in Download still appears; no other file types listed; files added/deleted outside the app appear/disappear after returning to the app; indexing never runs on the main thread (StrictMode clean).
  - Src: BRD R2-3, NFR-1 · Files: `data/files/FileIndex.kt`, `FileRepository.kt` · Deps: FR-010.
- [ ] **FR-012 Type chips & badges** — Status: not done
  - AC: chips All/PDF/Word/Excel/PPT filter the current tab; selected chip tinted with its type colour; badges PDF red, DOC blue, XLS green, PPT orange; Word covers doc/docx, Excel xls/xlsx/csv, PPT ppt/pptx.
  - Src: BRD R2-1/R2-2, S07–S10 · Files: `ui/components/TypeChips.kt`, `TypeBadge.kt` · Deps: FR-011.
- [ ] **FR-013 File row** — Status: not done
  - AC: row shows badge, name with extension (ellipsised), `yy/MM/dd • size` (e.g. `26/09/22 • 219.8 KB`), star, ⋮; tap opens the right viewer; long-press enters selection.
  - Src: BRD R2-2, S07 · Files: `ui/components/FileRow.kt` · Deps: FR-012.
- [ ] **FR-014 Sort** — Status: not done
  - AC: sort sheet offers Name/Date/Size and Ascending/Descending; list reorders immediately; choice persists across restarts; default Date descending.
  - Src: BRD R2-4 · Files: `ui/home/SortSheet.kt`, `data/prefs/AppPreferences.kt` · Deps: FR-011.
- [ ] **FR-015 Search screen** — Status: not done
  - AC: back arrow + "Search in file" field with clear (×); typing filters by name case-insensitively within 150 ms debounce; results use FileRow; empty query shows all; banner ad at bottom (FR-089).
  - Src: BRD R2-5, S02 · Files: `ui/search/SearchScreen.kt` · Deps: FR-013.
- [ ] **FR-016 Favourites** — Status: not done
  - AC: tapping star toggles favourite in every list instantly; Favourite tab lists starred files with chips; state survives restart; deleted files disappear from Favourite.
  - Src: BRD R2-6, S13 · Files: `data/files/FileMeta*.kt`, `AppDatabase.kt`, `ui/home/FavouriteTab.kt` · Deps: FR-013.
- [ ] **FR-017 Recent** — Status: not done
  - AC: opening any file records it; Recent shows newest first, max 100, with chips; missing files are dropped.
  - Src: BRD R2-3 · Files: `ui/home/RecentTab.kt`, `data/files/*` · Deps: FR-016.
- [ ] **FR-018 Share / Delete / File info** — Status: not done
  - AC: ⋮ → Share opens system sheet and the receiving app can read the file; Delete asks for confirmation, removes the file from storage and all lists; File info shows name, location, size, modified date and (PDF) page count.
  - Src: BRD R2-7 · Files: `ui/home/FileMenu.kt`, `data/files/FileRepository.kt`, `res/xml/file_paths.xml` · Deps: FR-013.
- [ ] **FR-019 Multi-select** — Status: not done
  - AC: selection mode shows count; Share sends all selected (ACTION_SEND_MULTIPLE); Delete removes all after one confirmation; back exits selection.
  - Src: BRD R1-2, R2-7 · Files: `ui/home/SelectionBar.kt` · Deps: FR-018.
- [ ] **FR-020 Sample files** — Status: not done
  - AC: a tab×chip list with no files shows one "Sample File" row of that type dated 01/01/2023; it opens in the viewer; it has no Delete action; it disappears when a real file of that type exists.
  - Src: BRD R2-8, S08, S10, S13 · Files: `data/files/SampleFiles.kt`, `app/src/main/assets/samples/*` · Deps: FR-013.
- [ ] **FR-021 Default opener (PDF, Word, Excel, PPT)** — Status: not done
  - AC: from Files/Gmail, "Open with" lists this app for pdf, doc, docx, xls, xlsx, ppt, pptx; choosing "Always" makes the app open that type next time without asking; the file opens in the right viewer (legacy formats show FR-036) and is added to Recent; Settings > "Set as default" opens the system open-by-default screen for this app (API 31+) or app details (older).
  - Src: BRD R3-6 · Files: `AndroidManifest.xml`, `MainActivity.kt`, `ui/settings/SettingsScreen.kt` · Deps: FR-030, FR-033, FR-080.

## Reader

- [ ] **FR-030 PDF viewer** — Status: not done
  - AC: 50 MB PDF shows first page ≤ 1 s; smooth continuous scroll at 60 fps; pinch zoom 1–5× and double-tap; "n / N" indicator; password PDF prompts and opens with the right password, shows error on wrong one; no OOM on a 1,000-page PDF.
  - Src: BRD R3-1, NFR-1 · Files: `pdf/PdfRenderSession.kt`, `pdf/PdfAccess.kt`, `pdf/PdfDecryptor.kt`, `ui/reader/PdfReaderScreen.kt`, `ui/reader/PdfPages.kt` (ports, TECH_SPEC §4) · Deps: FR-001.
- [ ] **FR-031 PDF search** — Status: not done
  - AC: search field finds all occurrences (case-insensitive), shows count, highlights hits, next/prev scrolls to each; works offline; text-less (scanned) PDF shows "No text found".
  - Src: BRD R3-2 · Files: `pdf/text/PdfTextIndex.kt`, `pdf/text/PdfWords.kt`, `ui/reader/*` · Deps: FR-030.
- [ ] **FR-032 Highlight** — Status: not done
  - AC: select words → Highlight → yellow highlight shown; after save, the file opened in another PDF app shows a standard highlight annotation; read-only source saves `<name>_highlighted.pdf` instead.
  - Src: BRD R3-3 · Files: `pdf/PdfMarkupWriter.kt`, `ui/reader/MarkupLayer.kt` · Deps: FR-031.
- [ ] **FR-033 DOCX viewer** — Status: not done
  - AC: sample DOCX shows headings, bold/italic/underline, bullets, tables and images in order; WebView has JS disabled; in-document search highlights matches; "Simplified view" label visible.
  - Src: BRD R3-5 · Files: `office/OoxmlZip.kt`, `office/DocxToHtml.kt`, `ui/reader/OfficeReaderScreen.kt` · Deps: FR-001.
- [ ] **FR-034 XLSX viewer** — Status: not done
  - AC: each sheet is a tab; shared and inline strings and numbers display in the right cells; > 5,000 rows shows a "first 5,000 rows" notice; opens a 5 MB workbook without ANR.
  - Src: BRD R3-5, S10 · Files: `office/XlsxToHtml.kt` · Deps: FR-033.
- [ ] **FR-035 PPTX viewer** — Status: not done
  - AC: one card per slide in slide order, with its text and pictures; slide number shown.
  - Src: BRD R3-5, S08 · Files: `office/PptxToHtml.kt` · Deps: FR-033.
- [ ] **FR-036 Legacy formats** — Status: not done
  - AC: opening .doc/.xls/.ppt shows the "older format" message and an "Open with" chooser; no crash when no other app handles it.
  - Src: BRD R3-5 · Files: `ui/reader/OfficeReaderScreen.kt` · Deps: FR-033.
- [ ] **FR-037 Reader actions** — Status: not done
  - AC: top bar back/name/search/share/star; overflow AI Translate, AI Summary (go to Select page with this file), Edit/Sign (crown for non-Pro → Paywall); no ads on reader.
  - Src: BRD R3-4, R8 · Files: `ui/reader/*` · Deps: FR-030, FR-062, FR-052.

## Converter

- [ ] **FR-040 Create PDF sheet** — Status: not done
  - AC: centre button opens sheet titled "Create PDF" with Image to PDF and Scan Document outlined buttons; each opens its flow; swipe down dismisses.
  - Src: BRD R1-3, S11 · Files: `ui/home/CreatePdfSheet.kt` · Deps: FR-003.
- [ ] **FR-041 Image to PDF** — Status: not done
  - AC: Photo Picker multi-select (no storage/media permission prompt); reorder by drag; remove image; page size A4/Letter/Fit and margin None/Small/Wide; EXIF rotation correct; 20 photos convert in ≤ 10 s; output opens in reader and appears in the library.
  - Src: BRD R4-1 · Files: `ui/imagetopdf/*`, `pdf/ImagesToPdf.kt` (from ps `ImagePdfRepository.imagesToPdf`) · Deps: FR-045, FR-030 · Play: Photo Picker satisfies photo permission policy.
- [ ] **FR-042 Camera scan** — Status: not done
  - AC: CAMERA requested on first use with rationale; denial shows settings link; live page outline tracks a sheet; auto-capture fires once per page and re-arms for the next sheet; flash toggle; gallery import adds pages; first capture never crashes on edge-hugging quads (ps memory.md regression).
  - Src: BRD R4-2 · Files: `ui/scan/*`, `imaging/*`, `app/src/main/assets/docaligner_lcnet100.tflite` (ports, TECH_SPEC §5) · Deps: FR-001 · Play: camera only while scanning.
- [ ] **FR-043 Scan review** — Status: not done
  - AC: corner drag with snapping; Apply edges warps with animation; rotate 90°; filters Original/Enhanced/Grey/B&W; reorder; delete with undo; add page returns to camera; state survives process death (session store).
  - Src: BRD R4-2 · Files: `ui/scan/review/*`, `ui/scan/ScanSessionStore.kt` · Deps: FR-042.
- [ ] **FR-044 Scan save** — Status: not done
  - AC: Save writes a multi-page PDF (one page per scan, correct order/filters) via `PdfWriter`; opens in reader; appears in library; session files deleted; Discard deletes session after confirmation.
  - Src: BRD R4-2, R4-3 · Files: `pdf/PdfWriter.kt`, `ui/scan/review/*` · Deps: FR-043, FR-045.
- [ ] **FR-045 Output folder & naming** — Status: not done
  - AC: outputs land in `Documents/PDF Reader/` visible to other file apps; names follow TECH_SPEC FR-045 with ` (n)` on collision; partially written files never visible (IS_PENDING).
  - Src: BRD R4-3 · Files: `data/files/OutputFolder.kt` · Deps: FR-011.

## Signature

- [ ] **FR-050 Signature store & pad** — Status: not done
  - AC: draw → Undo/Clear/Save; saved PNG has transparent background and dark ink in both themes; max 3 (4th shows "delete one first"); delete removes file; persists across restarts; Pro-gated.
  - Src: BRD R5-1 · Files: `sign/SignatureStore.kt`, `ui/sign/SignaturePad.kt`, `ui/sign/SignaturesScreen.kt` · Deps: FR-086.
- [ ] **FR-051 Import signature from photo** — Status: not done
  - AC: pick a photo of ink on white paper → stored signature has transparent background, cropped to ink with padding; a photo with no ink shows "No signature found".
  - Src: BRD R5-1 · Files: `sign/SignatureInk.kt` (from ps `SignatureInk.kt`) · Deps: FR-050.
- [ ] **FR-052 Place signature / text on PDF** — Status: not done
  - AC: choose page, add saved signature or text/date stamp; drag, pinch-resize (5–150 % page width), rotate; select + delete; switch pages keeping placements; boxes never lost off-page.
  - Src: BRD R5-2 · Files: `ui/sign/PlaceOnPdfScreen.kt`, `ui/sign/AnnotationLayer.kt`, `sign/AnnotationBounds.kt`, `ui/sign/StampDialog.kt` · Deps: FR-050, FR-030.
- [ ] **FR-053 Write signed PDF** — Status: not done
  - AC: output `<name>_signed.pdf` shows signature/text at the same position/size/rotation as on screen (incl. rotated and cropBox-offset pages) in another PDF viewer; original file byte-identical; existing text still selectable; unit/instrumented test compares placement within 1 pt.
  - Src: BRD R5-3 · Files: `pdf/PdfSigner.kt`, `pdf/PdfOverlay.kt` · Deps: FR-052, FR-045.

## AI on-device

- [ ] **FR-060 AI Assistant FAB & dialog** — Status: not done
  - AC: red "AI Assistant" extended FAB bottom-right on Document tab, crown badge for non-Pro; tap opens dialog "AI Assistant" with AI Translate (blue) and AI Summary (purple); outside tap dismisses.
  - Src: BRD R6-1, S03, S07 · Files: `ui/ai/AiAssistantDialog.kt`, `ui/home/DocumentTab.kt` · Deps: FR-003.
- [ ] **FR-061 AI file chooser** — Status: not done
  - AC: shows only PDFs from the library with search field; picking one opens Select page; sample PDF selectable.
  - Src: BRD R6-2, S02 · Files: `ui/ai/AiFilePicker.kt` · Deps: FR-060, FR-015.
- [ ] **FR-062 Select page screen** — Status: not done
  - AC: title "Select page", back arrow; left "N page(s)", right "Choose L page(s)" where L = FR-071 limit; thumbnails grid with round check; selecting beyond L shows limit message (+ Pro upsell for free users); Select button disabled (grey) until ≥ 1 selected, blue when enabled; native ad at bottom for free users (FR-090); back → Quit dialog if any page selected.
  - Src: BRD R6-3, S01, S05 · Files: `ui/ai/SelectPageScreen.kt` · Deps: FR-061, FR-030.
- [ ] **FR-063 Page text extraction** — Status: not done
  - AC: text PDF page returns its text in reading order; scanned Latin page returns OCR text (bundled model, works offline); scanned non-Latin page returns the "No readable text" result; runs off main thread; 1 page ≤ 2 s.
  - Src: TECH_SPEC §7 · Files: `ai/PageTextExtractor.kt`, `ocr/TextRecogniser.kt` · Deps: FR-030.
- [ ] **FR-064 Translate screen & language sheet** — Status: not done
  - AC: file card with thumbnail, "name.pdf - (Page n[, …])", "size - N page"; "Translate To" field opens a bottom sheet with exactly Italian, Polish, Portuguese, Dutch, French, German, Spanish (display names in app language, one Portuguese row, downloaded ones marked, size shown on the rest); "?" opens limits help; Translate button starts job with progress.
  - Src: BRD R6-4, S04 · Files: `ui/ai/TranslateScreen.kt`, `ui/ai/LanguageSheet.kt` · Deps: FR-062.
- [ ] **FR-065 Translation engine & models** — Status: not done
  - AC: a fresh install has no translation model downloaded; picking a missing language prompts with its size and Wi-Fi-only default, then downloads only that one with progress/cancel (cancel leaves it unselected); after download, translation works in airplane mode; source auto-detected, "und" asks the user; paragraph breaks preserved; network inspector shows no request containing document text.
  - Src: BRD R6-7, TECH_SPEC §7 · Files: `ai/Translator.kt` · Deps: FR-063 · Play: disclose model download in privacy policy (FR-102).
- [ ] **FR-066 AI result screen** — Status: not done
  - AC: selectable text; "AI-generated — may be inaccurate" on both Translate and Summary; Copy; Share; Save as PDF produces readable PDF for Latin, Cyrillic, Bengali, Devanagari and CJK text (glyphs shaped correctly); Report opens mail composer without attaching content; no ads.
  - Src: BRD R6-4/R6-5 · Files: `ui/ai/AiResultScreen.kt`, `pdf/TextToPdf.kt` · Deps: FR-045 · Play: AI-generated content policy (report mechanism).
- [ ] **FR-067 Quit dialog** — Status: not done
  - AC: back on Select page (with selection) or during processing shows "Quit Translating"/"Quit Summarizing", body "Are you sure you want to quit and discard the changes?", Cancel (grey) keeps state, Quit (red) cancels job and leaves.
  - Src: BRD R6-6, S05 · Files: `ui/ai/QuitDialog.kt` · Deps: FR-062.
- [ ] **FR-068 Summary model in the install** — Status: not done
  - AC: `:ai_summary_model` is an install-time pack (`deliveryType = "install-time"`) containing `Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf` (65,518,432 bytes, sha256 `3e6131f7…8d39`); a fresh install has the file with no in-app download; airplane mode on first launch still summarizes; path resolves; `bundletool --local-testing` works.
  - Src: TECH_SPEC §7 · Files: `ai_summary_model/build.gradle.kts`, `ai_summary_model/src/main/assets/Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf` (git-ignored, fetched by dev), `ai/SummaryModelDelivery.kt`, `settings.gradle.kts`, `app/build.gradle.kts` (`assetPacks`) · Deps: FR-001 · Play: install size includes the 65.5 MB file (NFR-6).
- [ ] **FR-069 Summary engine** — Status: not done
  - AC: one selected English page produces 2 or 3 paragraphs (not bullets, not a one-line label); tokens stream; cancel stops within 1 s; airplane mode works after the model is installed. No accuracy check — invented details are allowed. Runs through llama.cpp on CPU; the paragraph clamp in TECH_SPEC §7 always yields 2 or 3 paragraphs.
  - Src: BRD R6-5, TECH_SPEC §7 · Files: `ai/SummaryEngine.kt`, `ui/ai/SummaryScreen.kt`, `app/src/main/cpp/` (llama.cpp JNI) · Deps: FR-068, FR-063.
- [ ] **FR-070 Non-English summary pivot** — Status: dropped
  - AC: not built. The summary model writes the paragraphs directly. Non-English pages may come back in English or mixed.
  - Src: TECH_SPEC §7 · Deps: —.
- [ ] **FR-071 AI limits & device gate** — Status: not done
  - AC: free = 1 page/run for both; Pro = 10 pages (Summary, still 2–3 paragraphs) / 50 (Translate); device without arm64 or < 2 GB RAM sees "AI Summary isn't supported on this device" while Translate works. Summary needs no extra download.
  - Src: BRD R6-3, R6-7 · Files: `ai/AiLimits.kt` · Deps: FR-086.

## Settings / Pro / Ads

- [ ] **FR-080 Settings screen** — Status: not done
  - AC: matches S06: PDF Pro card (crown, subtitle, Free Trial button, 4 feature icons) hidden or replaced by "PDF Pro active" for Pro; rows Language (current value), Set as default (FR-021), Rate Us, Share, Privacy policy with red chevrons; "Privacy options" row only when UMP requires; banner ad at bottom for free users.
  - Src: BRD R7-1/R7-2, S06 · Files: `ui/settings/*` · Deps: FR-003.
- [ ] **FR-081 Rate Us** — Status: not done
  - AC: triggers Play In-App Review flow; if unavailable opens Play Store page; no pre-rating question or reward.
  - Src: BRD R7-2 · Files: `ui/settings/SettingsScreen.kt` · Deps: FR-080 · Play: no incentivised/gated ratings.
- [ ] **FR-082 Share app** — Status: not done
  - AC: system share sheet with app name + Play Store URL.
  - Src: BRD R7-2 · Files: `ui/settings/SettingsScreen.kt` · Deps: FR-080.
- [ ] **FR-083 Privacy policy** — Status: not done
  - AC: opens the configured URL in a browser; URL equals the Play Console privacy policy URL (single constant).
  - Src: BRD R7-2 · Files: `ui/settings/SettingsScreen.kt`, `res/values/strings.xml` · Deps: FR-080 · Play: policy URL required.
- [ ] **FR-084 Billing** — Status: not done
  - AC: products load from Play (license tester); purchase monthly/yearly succeeds and is acknowledged; entitlement survives offline restart; refund/expiry revokes Pro on next resume; Restore works on reinstall.
  - Src: BRD G6, TECH_SPEC §8 · Files: `billing/*` (from ps `core/billing`) · Deps: FR-001 · Play: Play Billing only for digital goods.
- [ ] **FR-085 Paywall** — Status: not done
  - AC: plan cards show localized price and period from ProductDetails; trial text shown only when a free-trial offer exists; renewal/cancel text and Manage subscription link visible before purchase; close button visible immediately; Restore, Terms, Privacy links present.
  - Src: BRD R7-3 · Files: `ui/paywall/PaywallScreen.kt` · Deps: FR-084 · Play: subscriptions policy (FR-103).
- [ ] **FR-086 Pro gating** — Status: not done
  - AC: with Pro: no ad views created, no crowns, Edit unlocked, AI limits raised; without Pro: Edit/limit actions open Paywall; toggling via debug entitlement flips all of these without restart.
  - Src: BRD R7-4, R8 · Files: `billing/Entitlement.kt`, all gated screens · Deps: FR-084.
- [ ] **FR-087 UMP consent** — Status: not done
  - AC: EEA/UK test geography shows consent form before any ad request; choice respected; Privacy options row reopens form; non-EEA no form.
  - Src: TECH_SPEC §8 · Files: `ads/AdConsent.kt` · Deps: FR-001 · Play: EU User Consent Policy.
- [ ] **FR-088 Ads init** — Status: not done
  - AC: `MobileAdsInitProvider` removed from merged manifest; SDK initialised only after consent and only for non-Pro; debug builds request test IDs only; release IDs come from build config.
  - Src: TECH_SPEC §8 · Files: `ads/Ads.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` · Deps: FR-087, FR-086.
- [ ] **FR-089 Bottom banner** — Status: not done
  - AC: anchored adaptive banner below the bottom bar on Document/Recent/Favourite/Setting and at the bottom of Search; zero height when not filled (S12); same AdView reused across tab switches; divider separates it from the nav bar; never on reader, camera, editor, AI screens, paywall.
  - Src: BRD R8, S02, S06, S07–S13 · Files: `ads/BannerAd.kt`, `ui/shell/*`, `ui/search/*` · Deps: FR-088 · Play: AdMob accidental-click placement rules.
- [ ] **FR-090 Select page native ad** — Status: not done
  - AC: small native ad (icon, headline, CTA, AdChoices) with "Ad" badge at the bottom of Select page for free users; grey skeleton while loading; collapses on failure; destroyed when leaving screen.
  - Src: BRD R8, S01 · Files: `ads/NativeAdSlot.kt`, `res/layout/native_ad_small.xml` · Deps: FR-088, FR-062 · Play: native ads must be labelled.

## Compliance

- [ ] **FR-100 Manifest & permissions** — Status: not done
  - AC: merged manifest contains only permissions in TECH_SPEC §9 table; FOREGROUND_SERVICE and ADSERVICES topics/attribution removed; `allowBackup=false`; ML Kit `TransportBackendDiscovery` removed; lint `MissingPermission`/`ProtectedPermissions` clean.
  - Src: TECH_SPEC §9 · Files: `AndroidManifest.xml`, `res/xml/backup_rules.xml`, `data_extraction_rules.xml` · Deps: FR-001.
- [ ] **FR-101 All files access fallback** — Status: not done
  - AC: build with `ALL_FILES_ACCESS=false` has no MANAGE_EXTERNAL_STORAGE in merged manifest and every library feature works on granted files/folders; explainer copy states why access is needed.
  - Src: TECH_SPEC FR-010, §9 · Files: `app/build.gradle.kts`, `data/files/*` · Deps: FR-010 · Play: Permissions declaration form.
- [ ] **FR-102 Data safety & privacy policy** — Status: not done
  - AC: Data safety answers drafted per TECH_SPEC FR-102 and checked against SDK disclosures (AdMob, UMP, Billing, ML Kit, llama.cpp); privacy policy text states on-device processing, model downloads, ad data, contact; both reviewed together.
  - Src: BRD NFR-3 · Files: none in app (store assets) · Deps: FR-088, FR-065, FR-068 · Play: User Data policy.
- [ ] **FR-103 Subscription disclosure review** — Status: not done
  - AC: screenshot review of Settings banner + paywall for a trial-eligible and a non-eligible tester matches Play subscription policy (price, period, trial, renewal, cancel, no misleading "free").
  - Src: BRD R7-3 · Deps: FR-085.
- [ ] **FR-104 Third-party notices** — Status: not done
  - AC: Apache-2.0 notice for the Minueza-2-96M model reachable from Settings; OSS/licence list there too (PdfBox, OpenCV, DocAligner, llama.cpp, Minueza-2-96M, ML Kit).
  - Src: TECH_SPEC FR-104 · Deps: FR-068.
- [ ] **FR-105 Release checks** — Status: not done
  - AC: `zipalign -c -P 16` passes for all .so in release AAB; R8 release build passes smoke test of open PDF, search, scan, image→PDF, sign, translate, summary; airplane-mode QA passes; Pro build shows zero ads; target audience set 13+ in Console.
  - Src: TECH_SPEC §9 · Deps: all.
