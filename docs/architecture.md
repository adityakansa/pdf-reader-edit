# Architecture

Structure only. Requirements = `BRD.md`, how-to = `TECH_SPEC.md`, status = `workdone.md`.

## Current (2026-09-29)

Android Studio "No Activity" template. Verified by reading the files and by the code graph (`graphify-out/`).

```
PDFreaderPDFFileedit/                rootProject.name "PDF reader & PDF File edit"
├─ settings.gradle.kts               include(":app"); foojay toolchain resolver
├─ build.gradle.kts                  android.application plugin (apply false)
├─ gradle/libs.versions.toml         agp 9.3.3, core-ktx 1.19.1, appcompat 1.8.0, material 1.14.0, junit, espresso
├─ gradle/wrapper                    Gradle 9.5.0
└─ app/
   ├─ build.gradle.kts               namespace/applicationId com.whats.web.scan.webscan.pdfreaderpdffileedit
   │                                 compileSdk 37, targetSdk 37, minSdk 24, Java 11, R8 disabled
   │                                 deps: appcompat, core-ktx, material
   ├─ src/main/AndroidManifest.xml   <application> only — no Activity, no permissions
   ├─ src/main/res                   template theme (purple MaterialComponents), launcher icons, strings (app_name)
   ├─ src/main/keepRules/rules.keep  empty R8 rules
   ├─ src/test/.../ExampleUnitTest.kt
   └─ src/androidTest/.../ExampleInstrumentedTest.kt
```

No Kotlin source in `src/main`. No Compose, no DI, no persistence, no networking.

## Target

Single `:app` module, packages per feature (full list in TECH_SPEC §2). Two tooling-forced modules.

```mermaid
flowchart TB
  subgraph app[":app  com.whats.web.scan.webscan.pdfreaderpdffileedit"]
    MA[MainActivity<br/>AppCompatActivity + Compose] --> NAV[ui/shell AppNavHost]
    NAV --> HOME[ui/home tabs<br/>Document · Recent · Favourite]
    NAV --> SET[ui/settings] & PAY[ui/paywall] & SRCH[ui/search]
    NAV --> RD[ui/reader<br/>PDF · Office WebView]
    NAV --> SCAN[ui/scan + review] & I2P[ui/imagetopdf] & SIGN[ui/sign]
    NAV --> AIUI[ui/ai<br/>select page · translate · summary · result]

    HOME & SRCH --> FILES[data/files<br/>FileIndex · FileRepository · Room file_meta]
    RD --> PDF[pdf/<br/>PdfRenderSession · PdfTextIndex · PdfMarkupWriter]
    RD --> OFF[office/<br/>Docx/Xlsx/PptxToHtml]
    SCAN --> IMG[imaging/<br/>DocumentDetector · PerspectiveWarper · PageFilters]
    SCAN & I2P --> PDFW[pdf/PdfWriter] --> OUT[data/files/OutputFolder]
    SIGN --> SS[sign/SignatureStore] & SIGNER[pdf/PdfSigner] --> OUT
    AIUI --> AI[ai/<br/>PageTextExtractor · Translator · SummaryEngine]
    AI --> OCR[ocr/TextRecogniser]
    HOME & SET & SRCH & AIUI --> ADS[ads/<br/>AdConsent · BannerAd · NativeAdSlot]
    ADS & AIUI & SIGN --> BILL[billing/<br/>BillingManager · Entitlement]
  end
  IMG --> CV[":third-party:opencv<br/>(vendored, generated)"]
  AI --> PACK[":ai_summary_model<br/>install-time pack<br/>Minueza-2-96M Q2_K GGUF ~65.5 MB"]
```

### External libraries and what touches them

| Library | Used by |
|---|---|
| PdfRenderer (platform) | `pdf/PdfRenderSession` — page bitmaps, thumbnails |
| PdfBox-Android | `pdf/` text index, highlight annotations, `PdfWriter`, `PdfSigner`, decrypt |
| `android.graphics.pdf.PdfDocument` | `pdf/TextToPdf` — AI output with any script |
| CameraX, LiteRT (DocAligner), OpenCV | `ui/scan`, `imaging/` |
| ML Kit text-recognition (Latin, bundled) | `ocr/` |
| ML Kit translate + language-id | `ai/Translator` |
| llama.cpp via JNI (Minueza-2-96M Q2_K GGUF) | `ai/SummaryEngine` |
| Play Asset Delivery (install-time pack) | `ai/SummaryModelDelivery` |
| Play Billing, AdMob, UMP, In-App Review | `billing/`, `ads/`, `ui/settings` |
| Room, DataStore | `data/files`, `data/prefs` |

## Data flows

### Scan → PDF

```mermaid
sequenceDiagram
  participant C as CameraScreen/CameraViewModel
  participant A as DocumentEdgeAnalyzer
  participant D as DocumentDetector
  participant S as ScanSessionStore
  participant R as PageReviewViewModel
  participant W as PdfWriter
  participant O as OutputFolder
  C->>A: ImageAnalysis frames (luma, ~15 fps)
  A->>D: detect(luma) — OpenCV edges
  D-->>C: Quad → EdgeOutlineOverlay, StabilityTracker (auto-capture)
  C->>D: detect(still) — DocAligner TFLite, OpenCV fallback
  C->>S: page file + crop quad (JSON manifest, noBackupFilesDir)
  S->>R: session pages
  R->>R: CropEditor/QuadEditing, rotate, PageFilters, PerspectiveWarper
  R->>W: PageProcessor → JPEG per page → addJpegPage
  W->>O: MediaStore insert Documents/PDF Reader (IS_PENDING)
  O-->>R: content URI → open in reader, session deleted
```

### Signature → signed PDF

```mermaid
sequenceDiagram
  participant P as SignaturePad / SignatureInk
  participant St as SignatureStore
  participant L as PlaceOnPdfScreen + AnnotationBounds
  participant G as PdfSigner
  P->>St: transparent PNG (filesDir/signatures, max 3)
  L->>St: list PNGs
  L->>L: page render (PdfRenderSession), boxes as page fractions (drag/pinch/rotate)
  L->>G: Map<page, List<PdfOverlay>>
  G->>G: PdfBox load → PDPageContentStream APPEND → map to cropBox/rotation → drawImage/text
  G-->>L: <name>_signed.pdf via OutputFolder (original untouched)
```

### On-device AI

```mermaid
flowchart LR
  F[PDF + selected pages] --> X[PageTextExtractor<br/>PdfBox text]
  X -- "< 20 chars" --> O[Render page → ML Kit OCR Latin]
  X & O --> T{Action}
  T -- Translate --> LID[ML Kit Language ID] --> MT[ML Kit Translator<br/>it pl pt nl fr de es<br/>~30 MB each, downloaded when picked]
  T -- Summary --> LLM[llama.cpp on CPU<br/>Minueza-2-96M Q2_K<br/>2 or 3 paragraphs]
  MT & LLM --> RES[AiResultScreen<br/>copy · share · TextToPdf · report]
```

No network call carries document text. Network is used for ads, billing, and the one-time translation-model download. The summary model is already on the device after install.

## Rules for implementers

- Screens own UI state in a ViewModel; anything that touches files, PDFs or models lives in `data/`, `pdf/`, `office/`, `imaging/`,
  `sign/`, `ai/` — never in composables.
- All IO/CPU work on `Dispatchers.IO`/`Default`; PdfRenderer access serialised per document (`PdfRenderSession` mutex).
- Colours, type and shapes only from `ui/theme`.
- Ad and Pro checks go through `billing/Entitlement` and `ads/` only.
- `:third-party:opencv` is generated — do not edit (rebuild per ps:`third-party/opencv/README.md`).
