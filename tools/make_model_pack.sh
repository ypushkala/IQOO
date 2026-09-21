#!/bin/sh
# Builds the one-file model pack (callguard-models.zip) that the app imports in a single step.
# Uncompressed on purpose (the models are already compressed; "zip -0" is fast to make and to read, and keeps sizes in the headers).
#   ./tools/make_model_pack.sh [out.zip]     expects: models/omnilingual/{model.int8.onnx,tokens.txt} and ~/Downloads/gemma3-1b-it-int4.task
# Share the zip by USB, memory card, Bluetooth or a chat app; or have a shop or phone maker place it on the phone. Then in the app:
# Setup > Language models > Choose model files, or open the zip from the Files app and choose CallGuard.
set -e
OUT="${1:-callguard-models.zip}"; OUT="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp models/omnilingual/model.int8.onnx models/omnilingual/tokens.txt "$T"/
cp "$HOME/Downloads/gemma3-1b-it-int4.task" "$T"/
rm -f "$OUT"; (cd "$T" && zip -0 -q "$OUT" model.int8.onnx tokens.txt gemma3-1b-it-int4.task)
ls -l "$OUT"; shasum -a 256 "$OUT"
