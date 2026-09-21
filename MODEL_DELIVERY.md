# How the on-device models reach a phone (no adb, no developer steps)

Models (Gemma 1B 555 MB, Omnilingual 366 MB, about 0.92 GB) are too large for the APK, so they are delivered separately. Whisper base (160 MB) is bundled in the APK.

| Route | Who it is for | Steps for the user | Status |
|---|---|---|---|
| **1. One-file model pack** (`callguard-models.zip`) | Sideload users, shops, family helpers, phone makers | Get one file (USB, memory card, Bluetooth, chat app, shop). Open it from the Files app and choose CallGuard, **or** Setup > Language models > choose the zip. Wait about 40 seconds. | **Built and tested on the F17:** from an empty models folder, all three files imported, all three checksums matched, and speech recognition (English and Telugu) and Gemma then worked from the imported files. Build the pack with `tools/make_model_pack.sh`. |
| **2. In-app download** (online build only) | Users with Wi-Fi who installed the online build | Setup > Language models > "Download models on Wi-Fi". Android's DownloadManager fetches in the background, resumes after drops, shows its own notification. Each file is checked against the built-in SHA-256 before use; a wrong file is deleted. | **Built, compiles, not run:** needs a host. Set `MODEL_BASE_URL` (HTTPS folder with the three files side by side: `gemma3-1b-it-int4.task`, `model.int8.onnx`, `tokens.txt`; a public GitHub release works) in `app/build.gradle.kts`. |
| **3. Play Asset Delivery** | Google Play route only | Automatic with install/first open | Not built. Only makes sense if Play becomes the route (see PLAY_POLICY_NOTES.md). |
| **4. Phone-maker preload** | OEM partners | None: the maker ships the models with the system image or in their app. Simplest is to copy the three files into the app's models folder at first boot, or bundle the pack and let the app import it. | Design only. |

## Before distributing models publicly
- **Licences (to verify):** Gemma has its own Terms of Use and prohibited-use policy that apply when redistributing the file, and the Omnilingual model has its own licence. Read both and pass the terms on before hosting or bundling either.
- **Host and integrity:** the app trusts only files whose SHA-256 matches the built-in list (`ModelSlot.knownSha256`). When a model is updated, add the new hash in the same release. The zip import accepts unknown files but labels them "not verified"; the download route rejects them.
- **Cost of a bad update:** a corrupt model means no protection, so keep the previous model until the new one is fully verified (both routes already write to a temporary file first).

## Still to improve
- Smaller models would remove most of this friction (a quantised Gemma under 300 MB or dropping Gemma for a rules-only mode on low-storage phones). The app already works in rules-only mode when the models are missing.
- A "Wi-Fi share" from a family member's phone (nearby sharing) is possible later but adds permissions.

## Hackathon plan (venue Wi-Fi, no access to the iQOO phone until the event)
Recommended host: **a GitHub release in your own repository** (free, HTTPS, up to 2 GB per file; the three model files, the zip pack and the app APK all fit).
1. **Before the event:** `./tools/release_assets.sh release-assets`, then create the release and upload those files plus the app APK. (Make the repository/release public only after you have read the Gemma and Omnilingual licence terms; a private release cannot be downloaded by the app.)
2. Build the online app pointing at your release: `./gradlew assembleOnlineDebug -PmodelBaseUrl=https://github.com/<you>/<repo>/releases/download/<tag>/` (the URL must end with a slash). Upload that APK to the same release.
3. **At the venue, on the iQOO phone (Wi-Fi):** open the release page in the phone's browser, download and install the APK (allow installs from the browser when asked), open CallGuard and tap "Get ready". The language step downloads the files over Wi-Fi (about 0.9 GB, roughly 2 to 5 minutes on typical venue Wi-Fi) and checks each one before use.
4. **Backups in case the Wi-Fi is bad:** keep the APK and `callguard-models.zip` on your laptop and copy them to the phone over USB cable (file transfer mode), then open the zip from the Files app and choose CallGuard.
5. **Test on the real iQOO phone as early as possible:** the auto-start page entries for vivo/iQOO are unverified guesses, and its background-app and microphone behaviour is unknown.
