#!/usr/bin/env bash
# Builds the demo from Playwright scene clips (see video/SCRIPT.md). Requires ffmpeg and Chrome/Chromium.
# usage: assemble.sh <clips-dir> <architecture.png> <out.mp4>
# AI scenes (06-plan, 07-concept, 08-suggestions) that were not recorded become "not recorded" slates.
set -euo pipefail
CLIPS=$1; ARCH=$2; OUT=$3; WORK=$(mktemp -d)
CHROME=${CHROME:-$(ls -d ~/Library/Caches/ms-playwright/chromium-*/chrome-mac*/*.app/Contents/MacOS/* 2>/dev/null | grep -iv helper | head -1)}

slate() { # name seconds title subtitle
  cat > "$WORK/$1.html" <<HTML
<html><body style="margin:0;width:1920px;height:1080px;display:flex;flex-direction:column;justify-content:center;
align-items:center;background:#f4f3ee;color:#1f2a1d;font-family:Helvetica,Arial,sans-serif;text-align:center">
<div style="font-size:88px;font-weight:600;max-width:1600px;line-height:1.1">$3</div>
<div style="font-size:40px;margin-top:36px;color:#5b6152;max-width:1500px;line-height:1.35">$4</div></body></html>
HTML
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1920,1080 \
    --screenshot="$WORK/$1.png" "file://$WORK/$1.html" >/dev/null 2>&1
  still "$WORK/$1.png" "$2" "$1"
}
still() { # image seconds name
  ffmpeg -loglevel error -y -loop 1 -t "$2" -i "$1" \
    -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=white,fps=30,format=yuv420p" \
    -c:v libx264 "$WORK/$3.mp4"
}
clip() { # scene-dir name
  local f; f=$(find "$CLIPS/$1" -name '*.webm' 2>/dev/null | head -1)
  [ -n "$f" ] || return 1
  ffmpeg -loglevel error -y -i "$f" -vf "scale=1920:1080,fps=30,format=yuv420p" -c:v libx264 -an "$WORK/$2.mp4"
}
pending() { slate "$1" 6 "[NOT RECORDED: run with LIVE_AI=1]" "$2"; }

slate 00 20 "[PLACEHOLDER] Team intro" "Roomify · Team Bootcamp2Roomify — names and roles"
slate 01 20 "Roomify" "Redesign the room you actually have.<br>Students and renters can't picture a redesign in their room, can't see the total cost, can't drill or paint, and throw away usable furniture."
clip 01-signup 02; clip 02-create-upload 03; clip 03-review 04; clip 04-preferences 05; clip 05-dashboard 06
clip 06-plan 07 || pending 07 "Roomify redesign plan: keeps what you kept, fits the budget, rental-safe"
clip 07-concept 08 || pending 08 "Roomify visual concept: your own photo, redesigned"
clip 08-suggestions 09 || pending 09 "Roomify suggestions, cheaper options and a DIY guide within budget"
still "$ARCH" 15 10
slate 11 15 "Roomify · Measured results" "Detection on 60 held-out COCO rooms: precision 0.70 · recall 0.62 · 32 ms per photo<br>AI features in this build: demo mode (pre-generated results, labelled Demo in the app)"
slate 12 10 "[PLACEHOLDER] Thank you + voice-over credits" "Roomify · github.com/Bootcamp2Roomify"

for f in $(ls "$WORK"/*.mp4 | sort); do echo "file '$f'"; done > "$WORK/list.txt"
ffmpeg -loglevel error -y -f concat -safe 0 -i "$WORK/list.txt" -c copy "$OUT"
echo "$OUT: $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT" | awk '{printf "%.0f s", $1}')"
