#!/usr/bin/env bash
# Builds roomify-demo.mp4 from Playwright clips and title slates. Requires ffmpeg.
# usage: assemble.sh <clips-dir> <architecture.png> <out.mp4>
set -euo pipefail
CLIPS=$1; ARCH=$2; OUT=$3; WORK=$(mktemp -d)
slate() { # text seconds name
  ffmpeg -loglevel error -f lavfi -i "color=c=0xf4f3ee:s=1920x1080:d=$2" \
    -vf "drawtext=text='$1':fontcolor=0x1f2a1d:fontsize=64:x=(w-text_w)/2:y=(h-text_h)/2" \
    -r 30 -pix_fmt yuv420p -c:v libx264 "$WORK/$3.mp4"
}
still() { # image seconds name
  ffmpeg -loglevel error -loop 1 -t "$2" -i "$1" \
    -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=white" \
    -r 30 -pix_fmt yuv420p -c:v libx264 "$WORK/$3.mp4"
}
norm() { # webm -> mp4 at 1080p30
  ffmpeg -loglevel error -i "$1" -vf "scale=1920:1080,fps=30" -pix_fmt yuv420p -c:v libx264 -an "$WORK/$2.mp4"
}
slate "[PLACEHOLDER] Team intro" 20 01
slate "Imagine your own room — on any budget, in any rental" 25 02
# Playwright writes one randomly named .webm per scene folder (01-signup, 02-…); keep scene order.
i=3; while IFS= read -r clip; do norm "$clip" "$(printf %02d $i)"; i=$((i+1)); done < <(find "$CLIPS" -name '*.webm' | sort)
still "$ARCH" 15 90
slate "Results: see EVALUATION.md" 15 91
slate "[PLACEHOLDER] Thank you" 10 99
for f in $(ls "$WORK"/*.mp4 | sort); do echo "file '$f'"; done > "$WORK/list.txt"
ffmpeg -loglevel error -f concat -safe 0 -i "$WORK/list.txt" -c copy "$OUT"
ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT" | awk '{printf "duration %.0fs\n", $1}'
