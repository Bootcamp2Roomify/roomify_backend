#!/usr/bin/env bash
# Builds the silent demo video from Playwright scene clips (see video/SCRIPT.md): title card, captioned
# scenes, architecture, results and an end card with the live URL. Requires ffmpeg and Chrome/Chromium.
# usage: assemble.sh <clips-dir> <architecture.png> <out.mp4> <live-url>
set -euo pipefail
CLIPS=$1; ARCH=$2; OUT=$3; URL=$4; WORK=$(mktemp -d)
CHROME=${CHROME:-$(ls -d ~/Library/Caches/ms-playwright/chromium-*/chrome-mac*/*.app/Contents/MacOS/* 2>/dev/null | grep -iv helper | head -1)}
FONT="Helvetica,Arial,sans-serif"

shot() { # html-file png transparent?
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1920,1080 \
    ${3:+--default-background-color=00000000} --screenshot="$2" "file://$1" >/dev/null 2>&1
}
card() { # name seconds title subtitle
  cat > "$WORK/$1.html" <<HTML
<html><body style="margin:0;width:1920px;height:1080px;display:flex;flex-direction:column;justify-content:center;
align-items:center;background:#f4f3ee;color:#1f2a1d;font-family:$FONT;text-align:center">
<div style="font-size:96px;font-weight:700;max-width:1640px;line-height:1.1">$3</div>
<div style="font-size:40px;margin-top:40px;color:#4d5446;max-width:1500px;line-height:1.4">$4</div></body></html>
HTML
  shot "$WORK/$1.html" "$WORK/$1.png"
  ffmpeg -loglevel error -y -loop 1 -t "$2" -i "$WORK/$1.png" -vf "fps=30,format=yuv420p" -c:v libx264 "$WORK/$1.mp4"
}
caption() { # name text -> transparent 1920x1080 PNG with a caption bar at the bottom
  cat > "$WORK/$1-cap.html" <<HTML
<html><body style="margin:0;width:1920px;height:1080px;background:transparent;font-family:$FONT">
<div style="position:absolute;left:0;right:0;bottom:56px;display:flex;justify-content:center">
<div style="background:rgba(20,28,18,.86);color:#fff;font-size:38px;line-height:1.35;padding:18px 34px;
border-radius:14px;max-width:1600px;text-align:center">$2</div></div></body></html>
HTML
  shot "$WORK/$1-cap.html" "$WORK/$1-cap.png" transparent
}
clip() { # scene-dir name caption — screen recording, or frame-NN.png stills (4 s each) when the scene has them
  local f
  if ls "$CLIPS/$1"/frame-*.png >/dev/null 2>&1; then
    f="$WORK/$2-stills.mp4"; : > "$WORK/$2-stills.txt"
    for png in $(ls "$CLIPS/$1"/frame-*.png | sort); do
      ffmpeg -loglevel error -y -loop 1 -t 4 -i "$png" -vf "scale=1920:1080,fps=30,format=yuv420p" -c:v libx264 "$png.mp4"
      echo "file '$png.mp4'" >> "$WORK/$2-stills.txt"
    done
    ffmpeg -loglevel error -y -f concat -safe 0 -i "$WORK/$2-stills.txt" -c copy "$f"
  else
    f=$(find "$CLIPS/$1" -name '*.webm' | head -1)
  fi
  [ -n "$f" ] || { echo "missing clip: $1" >&2; exit 1; }
  caption "$2" "$3"
  ffmpeg -loglevel error -y -i "$f" -i "$WORK/$2-cap.png" \
    -filter_complex "[0:v]scale=1920:1080,fps=30[v];[v][1:v]overlay=0:0,format=yuv420p" -c:v libx264 -an "$WORK/$2.mp4"
}
still() { # image seconds name caption
  caption "$3" "$4"
  ffmpeg -loglevel error -y -loop 1 -t "$2" -i "$1" -i "$WORK/$3-cap.png" -filter_complex \
    "[0:v]scale=1920:1000:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:0:color=white,fps=30[v];[v][1:v]overlay=0:0,format=yuv420p" \
    -c:v libx264 "$WORK/$3.mp4"
}

card 00 6 "Roomify" "Redesign the room you actually have.<br><span style='font-size:34px'>For students and renters: see a redesign of your own room, within your budget, without drilling or throwing furniture away.</span>"
clip 01-signup 01 "Sign up — every room is a private project under your account"
clip 02-create-upload 02 "Name the room, say where you live, upload one photo"
clip 03-review 03 "YOLO finds the furniture. You choose Keep, Replace, Remove or Unsure — and add what it missed"
clip 04-preferences 04 "Style, colours and a budget. Rental-Friendly Mode is on because it's a dorm"
clip 05-dashboard 05 "Everything is saved. Come back any time"
clip 06-plan 06 "The redesign plan follows your decisions and budget — and the server checks it before saving.<br>Demo mode: pre-generated results, labelled Demo"
clip 07-concept 07 "Visual concept. In demo mode it's an example photo for this room type, not an edit of your photo"
clip 08-suggestions 08 "Suggestions within budget, cheaper options and a beginner DIY guide with safety notes"
still "$ARCH" 9 09 "Three Vercel projects: Next.js · Spring Boot container · FastAPI + YOLO11n (ONNX) · Postgres on Neon"
card 10 7 "Measured" "Detection on 60 held-out COCO room photos:<br>precision 0.70 · recall 0.62 · 32 ms per photo on a laptop CPU<br><br><span style='font-size:32px'>AI features in this build run in demo mode (pre-generated results), so no AI accuracy, latency or cost is claimed.</span>"
card 11 7 "Try Roomify" "<b style='font-size:56px;color:#1f2a1d'>$URL</b><br><br>github.com/Bootcamp2Roomify"

for f in $(ls "$WORK"/??.mp4 | sort); do echo "file '$f'"; done > "$WORK/list.txt"
# Re-encode while joining: stream copy can break timestamps between stills and screen recordings.
ffmpeg -loglevel error -y -f concat -safe 0 -i "$WORK/list.txt" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -movflags +faststart "$OUT"
echo "$OUT: $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT" | awk '{printf "%.0f s", $1}')"
