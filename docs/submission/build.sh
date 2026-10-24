#!/usr/bin/env bash
# Builds every Roomify submission document into docs/submission/final/ (PDF + DOCX, plus the combined
# Submission Pack PDF). Requirements: pandoc 3.x, Node.js 20+, python3, curl, unzip.
#   ./build.sh            build
#   ./build.sh --preview  also write PNG previews of every PDF page to tooling/.cache/preview/
set -euo pipefail
cd "$(dirname "$0")"
SUB=$PWD; B=$SUB/tooling; C=$B/.cache; OUT=$SUB/final
export SOURCE_DATE_EPOCH=1792800000  # 2026-10-24: stable DOCX metadata

need() { command -v "$1" >/dev/null || { echo "missing: $1" >&2; exit 1; }; }
need pandoc; need node; need python3; need curl; need unzip
case "$(pandoc --version | head -1)" in "pandoc 3."*) ;; *) echo "pandoc 3.x required" >&2; exit 1;; esac

# 1. Pinned toolchain and fonts (Adobe releases, verified by SHA-256).
(cd "$B" && PUPPETEER_SKIP_DOWNLOAD=1 npm ci --silent --no-audit --no-fund && npx playwright install chromium >/dev/null)
mkdir -p "$C/fonts" "$C/html" "$OUT"
fetch() { # url sha256 file
  [ -f "$C/$3" ] && echo "$2  $C/$3" | shasum -a 256 -c --status && return
  curl -sSL -o "$C/$3" "$1"; echo "$2  $C/$3" | shasum -a 256 -c --status || { echo "checksum mismatch: $3" >&2; exit 1; }
}
fetch https://github.com/adobe-fonts/source-serif/releases/download/4.005R/source-serif-4.005_Desktop.zip \
  549fdb8f9a682bd06944298621404969f6de77c2e422ff3b8244a1dcd6a0c425 serif.zip
fetch https://github.com/adobe-fonts/source-code-pro/releases/download/2.042R-u/1.062R-i/1.026R-vf/TTF-source-code-pro-2.042R-u_1.062R-i.zip \
  0c85bac90d15c040b82939aa92bc8404420fccc02e37bbcb9c93a7f21abb52c6 code.zip
unzip -o -q -j "$C/serif.zip" 'source-serif-4.005_Desktop/TTF/SourceSerif4-Regular.ttf' 'source-serif-4.005_Desktop/TTF/SourceSerif4-It.ttf' \
  'source-serif-4.005_Desktop/TTF/SourceSerif4-Bold.ttf' 'source-serif-4.005_Desktop/TTF/SourceSerif4-BoldIt.ttf' \
  'source-serif-4.005_Desktop/TTF/SourceSerif4-Semibold.ttf' -d "$C/fonts"
unzip -o -q -j "$C/code.zip" 'TTF/SourceCodePro-Regular.ttf' -d "$C/fonts"

# 2. Diagrams (Mermaid sources in architecture/) in the document typeface.
CHROME=$(cd "$B" && node -e 'console.log(require("playwright").chromium.executablePath())')
printf '{"executablePath":"%s","args":["--no-sandbox"]}' "$CHROME" > "$C/puppeteer.json"
cat > "$C/mermaid.json" <<'JSON'
{"theme": "neutral", "fontFamily": "Source Serif 4",
 "themeVariables": {"fontFamily": "Source Serif 4", "fontSize": "16px"},
 "flowchart": {"nodeSpacing": 30, "rankSpacing": 40, "padding": 10},
 "sequence": {"actorMargin": 24, "width": 120, "boxMargin": 6, "messageMargin": 18, "mirrorActors": false,
              "actorFontSize": 15, "messageFontSize": 15},
 "er": {"fontSize": 14}}
JSON
printf '@font-face{font-family:"Source Serif 4";src:url("file://%s/SourceSerif4-Regular.ttf")}\n@font-face{font-family:"Source Serif 4";font-weight:700;src:url("file://%s/SourceSerif4-Bold.ttf")}\n' "$C/fonts" "$C/fonts" > "$C/mermaid.css"
for src in architecture/*.mmd; do
  for ext in svg png; do
    "$B/node_modules/.bin/mmdc" -q -p "$C/puppeteer.json" -c "$C/mermaid.json" -C "$C/mermaid.css" -b white \
      $([ $ext = png ] && echo "-s 2") -i "$src" -o "${src%.mmd}.$ext"
  done
done

# 3. Pandoc: one filter for every target so content, captions and figure numbers match.
pandoc -o "$C/pandoc-default.docx" --print-default-data-file reference.docx
python3 "$B/docx.py" reference "$C/pandoc-default.docx" "$C/reference.docx"
FOOTER=$(python3 -c 'import json;c=json.load(open("'"$B"'/documents.json"));print(c["release"]+" · "+c["date"])')
export ROOMIFY_CONFIG=$B/documents.json
python3 -c 'import json;[print(d["id"],d["source"],d["file"],d["title"],sep="\t") for d in json.load(open("'"$B"'/documents.json"))["documents"]]' |
while IFS=$'\t' read -r id source file title; do
  common=(--from gfm+implicit_figures --wrap=none --lua-filter "$B/filter.lua" --resource-path "$SUB")
  ROOMIFY_DOC=$id ROOMIFY_TARGET=html pandoc "$source" "${common[@]}" --to html5 -o "$C/html/$id.single.html"
  ROOMIFY_DOC=$id ROOMIFY_TARGET=pack pandoc "$source" "${common[@]}" --to html5 --id-prefix "$id-" -o "$C/html/$id.pack.html"
  ROOMIFY_DOC=$id ROOMIFY_TARGET=docx pandoc "$source" "${common[@]}" --to docx --reference-doc "$C/reference.docx" -o "$C/$id.docx"
  python3 "$B/docx.py" finish "$C/$id.docx" "$OUT/$file.docx" "$title" "$FOOTER" "$C/fonts"
  echo "$file.docx"
done

# 4. PDFs: Paged.js in Chromium (cover, running headers, page numbers, contents for the pack).
node "$B/render.mjs" "$SUB" "$C" "$OUT" "$@"
