#!/bin/sh
# Downloads the three large build inputs that are not stored in git: the sherpa-onnx library and the Whisper base model.
# They are published next to the models in the same release. Usage:
#   ./tools/fetch_assets.sh https://github.com/<owner>/<repo>/releases/download/<tag>/
# Each file is checked against tools/assets.sha256 and the script stops if one does not match.
set -e
BASE="${1:-$ASSETS_BASE_URL}"
[ -n "$BASE" ] || { echo "usage: $0 <release-download-url-ending-with-/>"; exit 1; }
cd "$(dirname "$0")/.."
get() { # name destination
  mkdir -p "$(dirname "$2")"
  [ -f "$2" ] || curl -L --fail -o "$2" "$BASE$1"
  want=$(grep " $1\$" tools/assets.sha256 | cut -d' ' -f1)
  have=$(shasum -a 256 "$2" | cut -d' ' -f1)
  [ "$want" = "$have" ] || { echo "CHECKSUM MISMATCH for $1"; rm -f "$2"; exit 1; }
  echo "ok  $1"
}
get sherpa-onnx-1.13.8.aar app/libs/sherpa-onnx-1.13.8.aar
get base-encoder.int8.onnx app/src/main/assets/asr/base-encoder.int8.onnx
get base-decoder.int8.onnx app/src/main/assets/asr/base-decoder.int8.onnx
