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
.grid-wrap { overflow-x: auto; margin: 0 -16px; }
table.grid { display: table; width: auto; margin: 0; border-collapse: separate; border-spacing: 0; font-size: 13px; }
table.grid th, table.grid td { border: 0; border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;
  padding: 4px 8px; white-space: nowrap; max-width: 280px; overflow: hidden; text-overflow: ellipsis; }
table.grid thead th { position: sticky; top: 0; background: #f3f3f3; color: #666; font-weight: 500; text-align: center; z-index: 1; }
table.grid th.rownum, table.grid th.corner { position: sticky; left: 0; background: #f3f3f3; color: #666; font-weight: 500;
  text-align: center; min-width: 32px; z-index: 2; }
table.grid td.num { text-align: right; }
.plain { white-space: pre-wrap; font-size: 15px; line-height: 1.6; }
.notice { color: #9e9e9e; font-size: 13px; margin: 8px 0; }
ul { margin: 4px 0 4px 20px; padding: 0; }
@media (prefers-color-scheme: dark) {
  body { color: #ececec; background: #121212; }
  td, th { border-color: #333; }
  .slide { border-color: #2a2a2a; }
  .sheet-tabs { border-color: #333; background: #121212; }
  .sheet-tabs label { color: #aaa; }
  table.grid th, table.grid td { border-color: #333; }
  table.grid thead th, table.grid th.rownum, table.grid th.corner { background: #1e1e1e; color: #aaa; }
}
</style>
</head><body>
$body
</body></html>
"""
}
