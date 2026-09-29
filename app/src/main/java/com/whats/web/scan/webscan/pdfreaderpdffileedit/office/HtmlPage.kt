package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

/** The shared shell for every converted document: one stylesheet, no script, readable on a phone. */
object HtmlPage {
    fun wrap(body: String, bodyClass: String = ""): String = """
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
body.desk { background: #e9e9ec; padding: 12px 10px 24px; }
body.deck { background: #e9e9ec; padding: 8px 10px 24px; }
body.deck .slide-number { margin: 10px 2px 6px; }
.deck-slide { position: relative; width: 100%; height: 0; container-type: inline-size; background: #ffffff; color: #000000;
  border-radius: 4px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.2), 0 4px 12px rgba(0,0,0,.06);
  font-family: 'Calibri', 'Carlito', 'Roboto', sans-serif; }
.deck-slide .shape { position: absolute; display: flex; flex-direction: column; overflow: visible; box-sizing: border-box;
  padding: 0.7cqw 1.2cqw; line-height: 1.15; overflow-wrap: anywhere; }
.deck-slide .shape p { margin: 0 0 0.4em; }
.deck-slide .shape .bullet { display: inline-block; width: 1.1em; }
.deck-slide .shape-img { position: absolute; object-fit: fill; max-width: none; }
.doc-page { background: #ffffff; color: #1a1a1a; border-radius: 2px; padding: 28px 22px 32px; margin: 0 auto 14px;
  max-width: 820px; box-shadow: 0 1px 3px rgba(0,0,0,.18), 0 4px 12px rgba(0,0,0,.06); font-size: 14.7px; line-height: 1.45;
  font-family: 'Calibri', 'Carlito', 'Roboto', sans-serif; overflow-wrap: anywhere; }
.doc-page p { margin: 0 0 8px; }
.doc-page h1, .doc-page h2, .doc-page h3 { margin: 14px 0 8px; }
.doc-page h1.title { font-size: 28px; font-weight: 400; margin-top: 0; }
.doc-page p.li { margin: 0 0 4px; }
.doc-page .marker { display: inline-block; text-align: left; }
.doc-page img { max-width: 100%; height: auto; }
table.doc-table { display: table; width: 100%; border-collapse: collapse; margin: 8px 0 12px; }
table.doc-table td { border: 1px solid #bfbfbf; padding: 4px 6px; vertical-align: top; }
table.doc-table td p { margin: 0 0 2px; }
.notice { color: #9e9e9e; font-size: 13px; margin: 8px 0; }
ul { margin: 4px 0 4px 20px; padding: 0; }
@media (prefers-color-scheme: dark) {
  body { color: #ececec; background: #121212; }
  td, th { border-color: #333; }
  .slide { border-color: #2a2a2a; }
  .sheet-tabs { border-color: #333; background: #121212; }
  .sheet-tabs label { color: #aaa; }
  body.desk, body.deck { background: #121212; }
  table.grid th, table.grid td { border-color: #333; }
  table.grid thead th, table.grid th.rownum, table.grid th.corner { background: #1e1e1e; color: #aaa; }
}
</style>
</head><body class="$bodyClass">
$body
</body></html>
"""
}
