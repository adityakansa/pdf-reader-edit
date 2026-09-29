#!/usr/bin/env bash
# FR-068: downloads the summary model into the install-time asset pack and checks it byte for byte.
set -euo pipefail

NAME="Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf"
URL="https://huggingface.co/mradermacher/Minueza-2-96M-Instruct-Variant-04-GGUF/resolve/main/$NAME"
SHA256="3e6131f72e6795981798b91ed11eb118d1b830a2fc5a159723aecca498ed8d39"
BYTES=65518432

DIR="$(cd "$(dirname "$0")/.." && pwd)/ai_summary_model/src/main/assets"
TARGET="$DIR/$NAME"
mkdir -p "$DIR"

if [ ! -f "$TARGET" ]; then
    curl -fL --retry 3 -o "$TARGET.part" "$URL"
    mv "$TARGET.part" "$TARGET"
fi

size=$(wc -c < "$TARGET" | tr -d ' ')
[ "$size" = "$BYTES" ] || { echo "size $size != $BYTES" >&2; exit 1; }
if command -v sha256sum >/dev/null; then sum=$(sha256sum "$TARGET" | cut -d' ' -f1)
else sum=$(shasum -a 256 "$TARGET" | cut -d' ' -f1); fi
[ "$sum" = "$SHA256" ] || { echo "sha256 mismatch: $sum" >&2; exit 1; }
echo "OK: $TARGET"
