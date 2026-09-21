#!/bin/sh
# Signing helper for the optional online build's scam-number list. Keep the private key OFFLINE; only the public key goes in the app.
#   ./tools/pack.sh newkey  key.pem          create a P-256 key, print the base64 PUBLIC key (paste into PACK_PUBLIC_KEY in app/build.gradle.kts)
#   ./tools/pack.sh sign key.pem pack.txt    write pack.txt.sig (host pack.txt and pack.txt.sig next to each other; PACK_URL points at pack.txt)
# pack.txt = "version|N" line + "prefix|weight|label" lines (same as app/src/main/assets/scam_prefixes.txt). Raise N on every release.
set -e
case "$1" in
  newkey) openssl ecparam -genkey -name prime256v1 -noout -out "$2"; openssl ec -in "$2" -pubout -outform DER 2>/dev/null | base64 | tr -d '\n'; echo ;;
  sign)   openssl dgst -sha256 -sign "$2" "$3" | base64 | tr -d '\n' > "$3.sig"; echo "wrote $3.sig" ;;
  *) echo "usage: $0 newkey key.pem | sign key.pem pack.txt"; exit 1 ;;
esac
