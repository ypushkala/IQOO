#!/bin/sh
# Prepares everything to publish for the hackathon: the online app (downloads its own language files) and the language files.
# Nothing is uploaded by this script. Publish the folder it creates as a GitHub release (or any HTTPS host), then build the app
# pointing at it:   ./gradlew assembleOnlineDebug -PmodelBaseUrl=https://github.com/<you>/<repo>/releases/download/<tag>/
#   1) ./tools/release_assets.sh <folder>          builds the files below
#   2) gh release create <tag> <folder>/* --repo <you>/<repo>      (or drag the files into a release on github.com)
#   3) rebuild the app with -PmodelBaseUrl=... (it must end with a slash), publish that APK too
# Files: sherpa-onnx-1.13.8.aar, base-encoder/decoder.int8.onnx (build inputs), gemma3-1b-it-int4.task, model.int8.onnx, tokens.txt (used by the in-app download, each checked by SHA-256),
#        callguard-models.zip (the one-file pack for sharing), SHA256SUMS.txt
set -e
OUT="${1:-release-assets}"; mkdir -p "$OUT"
cp models/omnilingual/model.int8.onnx models/omnilingual/tokens.txt "$OUT"/
cp "$HOME/Downloads/gemma3-1b-it-int4.task" "$OUT"/
# build inputs that are not in git (see tools/fetch_assets.sh)
cp app/libs/sherpa-onnx-1.13.8.aar app/src/main/assets/asr/base-encoder.int8.onnx app/src/main/assets/asr/base-decoder.int8.onnx "$OUT"/
./tools/make_model_pack.sh "$OUT/callguard-models.zip" >/dev/null
(cd "$OUT" && shasum -a 256 * > SHA256SUMS.txt && ls -l)
echo "Read the Gemma and Omnilingual licence terms before making these files public."
