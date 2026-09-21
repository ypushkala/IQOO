# Hackathon checklist (venue Wi-Fi, iQOO phone only at the venue)

## Before the event (on your laptop)
1. `release-assets/` is already prepared: three model files, `callguard-models.zip`, `CallGuard-offline.apk`, `SHA256SUMS.txt`. (Rebuild with `./tools/release_assets.sh release-assets` if models change.)
2. Read the Gemma and Omnilingual licence terms before making any of these public.
3. Create a GitHub repository and a release (for example tag `models-v1`), then upload the files:
   `gh release create models-v1 release-assets/* --repo <you>/<repo> --title "CallGuard demo files"`
   (or drag the files into a release on github.com; each file is under the 2 GB limit).
4. Build the online app that downloads its own language files from that release (URL must end with a slash):
   `./gradlew assembleOnlineDebug -PmodelBaseUrl=https://github.com/<you>/<repo>/releases/download/models-v1/`
   Upload `app/build/outputs/apk/online/debug/app-online-debug.apk` to the same release as `CallGuard-online.apk`.
5. Open the release page on your own phone, download the online APK, install it, tap Get ready and check that the language files download and each shows "ready ✓".

## At the venue (iQOO phone, Wi-Fi)
1. Phone browser: open the release page, download `CallGuard-online.apk`, install (allow installs from the browser when asked).
2. Open CallGuard, tap Get ready and follow the steps. The language files download by themselves (about 0.9 GB).
3. Note anything odd on the extra background step (the iQOO page names are unverified) and whether the microphone works during a real call.

## If the Wi-Fi is bad
Copy `CallGuard-offline.apk` and `callguard-models.zip` from the laptop to the phone by USB cable (file transfer mode). Install the APK, open the zip from the Files app and choose CallGuard, then follow Get ready.

## Bring
Laptop with `release-assets/`, USB-C cable, a second phone for the real-call tests, the printed `USABILITY_TEST.md` if you will time people.
