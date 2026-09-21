#!/bin/sh
# Pushes the on-device models to the phone's CallGuard app storage (nothing is bundled in the APK).
# Usage:  ./push_models.sh [device-serial]      Gemma is expected in ~/Downloads (gemma3-1b-it-int4.task).
ADB=~/Library/Android/sdk/platform-tools/adb
D="${1:+-s $1}"
DEST=/sdcard/Android/data/com.callguard/files/models
$ADB $D shell mkdir -p $DEST/omnilingual
[ -f models/omnilingual/model.int8.onnx ] && $ADB $D push models/omnilingual/model.int8.onnx models/omnilingual/tokens.txt $DEST/omnilingual/
[ -f ~/Downloads/gemma3-1b-it-int4.task ] && $ADB $D push ~/Downloads/gemma3-1b-it-int4.task $DEST/
$ADB $D shell ls -l $DEST $DEST/omnilingual
