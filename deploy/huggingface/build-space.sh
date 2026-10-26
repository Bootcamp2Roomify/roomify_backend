#!/usr/bin/env bash
# Assembles the Hugging Face Space folder: Dockerfile, start.sh, README with Space metadata,
# backend sources and CV sources. usage: build-space.sh <roomify_cv dir> <out dir>
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); BACKEND=$(cd "$HERE/../.." && pwd); CV=$(cd "$1" && pwd); OUT=$2
rm -rf "$OUT"; mkdir -p "$OUT/backend" "$OUT/cv"
cp "$HERE/Dockerfile" "$HERE/start.sh" "$OUT/"; cp "$HERE/README.space.md" "$OUT/README.md"
cp -R "$BACKEND/pom.xml" "$BACKEND/src" "$OUT/backend/"
cp -R "$CV/app" "$CV/requirements.txt" "$CV/requirements.lock" "$OUT/cv/"
find "$OUT" -name '__pycache__' -prune -exec rm -rf {} +
echo "$OUT"
