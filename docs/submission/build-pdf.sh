#!/usr/bin/env bash
# Builds REPORT.pdf from REPORT.md. Needs Node (npx) and a Chrome/Chromium binary (CHROME=...).
set -euo pipefail
cd "$(dirname "$0")"
CHROME=${CHROME:-$(ls -d ~/Library/Caches/ms-playwright/chromium-*/chrome-mac*/*.app/Contents/MacOS/* 2>/dev/null | grep -iv helper | head -1)}
BODY=$(npx -y marked@15.0.12 --gfm -i REPORT.md)
cat > REPORT.html <<HTML
<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Roomify — technical report</title>
<style>
  @page { size: A4; margin: 18mm 16mm; }
  body { font: 10.5pt/1.5 -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #1f2a1d; }
  h1 { font-size: 22pt; margin: 0 0 4pt; } h2 { font-size: 14pt; margin-top: 18pt; border-bottom: 1px solid #d5d8cb; padding-bottom: 3pt; }
  h3 { font-size: 11.5pt; margin-top: 12pt; } blockquote { margin: 8pt 0; padding: 6pt 10pt; background: #fff7e0; border-left: 3px solid #d39b1a; }
  table { border-collapse: collapse; width: 100%; margin: 6pt 0; font-size: 9pt; page-break-inside: avoid; }
  th, td { border: 1px solid #d5d8cb; padding: 3pt 5pt; text-align: left; vertical-align: top; } th { background: #f0f1ea; }
  img { max-width: 100%; page-break-inside: avoid; } code { font-size: 8.5pt; background: #f0f1ea; padding: 0 2pt; }
  a { color: #1d4ed8; text-decoration: none; }
</style></head><body>${BODY}
<p style="margin-top:24pt;font-size:8pt;color:#6b7280">Generated $(date -u +%Y-%m-%dT%H:%MZ) from REPORT.md.</p></body></html>
HTML
"$CHROME" --headless=new --disable-gpu --no-pdf-header-footer --print-to-pdf=REPORT.pdf "file://$PWD/REPORT.html" 2>/dev/null
rm REPORT.html
echo "REPORT.pdf $(wc -c < REPORT.pdf) bytes"
