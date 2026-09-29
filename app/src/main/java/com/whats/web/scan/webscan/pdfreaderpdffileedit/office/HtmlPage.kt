package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

/** The shared shell for every converted document: one stylesheet, no script, readable on a phone. */
object HtmlPage {
    fun wrap(body: String): String = """
<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root { color-scheme: light dark; }
body { margin: 0; padding: 16px; font-family: sans-serif; font-size: 16px; line-height: 1.5;
       color: #1a1a1a; background: #ffffff; word-wrap: break-word; }
h1,h2,h3,h4,h5,h6 { line-height: 1.25; }
img { max-width: 100%; height: auto; }
table { border-collapse: collapse; width: 100%; margin: 12px 0; display: block; overflow-x: auto; }
td, th { border: 1px solid #ddd; padding: 6px 8px; font-size: 14px; vertical-align: top; }
.slide { border: 1px solid #e0e0e0; border-radius: 12px; padding: 16px; margin: 0 0 16px; }
.slide-number { color: #9e9e9e; font-size: 12px; margin-bottom: 8px; }
.sheet-radio { position: absolute; opacity: 0; pointer-events: none; }
.sheet-tabs { display: flex; overflow-x: auto; border-bottom: 1px solid #ddd; position: sticky; top: 0;
              background: #ffffff; margin: -16px -16px 8px; padding: 0 8px; }
.sheet-tabs label { flex: none; padding: 12px 16px; border-bottom: 3px solid transparent; color: #666;
                    font-weight: 600; white-space: nowrap; cursor: pointer; }
.sheet-panel { display: none; }
.notice { color: #9e9e9e; font-size: 13px; margin: 8px 0; }
ul { margin: 4px 0 4px 20px; padding: 0; }
@media (prefers-color-scheme: dark) {
  body { color: #ececec; background: #121212; }
  td, th { border-color: #333; }
  .slide { border-color: #2a2a2a; }
  .sheet-tabs { border-color: #333; background: #121212; }
  .sheet-tabs label { color: #aaa; }
}
</style>
</head><body>
$body
</body></html>
"""
}
