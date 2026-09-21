# CallGuard

An on-device scam-call shield for Android. It listens to what is said on a call (speaker on), understands English, Hindi and Telugu, and warns you in your own language while the call is still happening. No audio or text leaves the phone: the product build has no internet permission.

**Status: a prototype.** The pipeline works end to end on a Samsung Galaxy F17 with played-back audio. Whether the far end's voice reaches the microphone during a real cellular call is not yet proven, and detection accuracy on real speech is not yet measured. See `PROJECT_SUMMARY.md` for the honest list of risks.

## Read first
- `PROJECT_SUMMARY.md` problem, stack, architecture, what is different, critical gaps
- `IMPLEMENTATION_PLAN.md` the plan and what has been verified
- `MODEL_DELIVERY.md` how the language models reach a phone; `HACKATHON_CHECKLIST.md` demo steps
- `PLAY_POLICY_NOTES.md` distribution route and Google Play policy notes; `USABILITY_TEST.md` setup test protocol

## Build
1. Get the three large build inputs (not in git): `./tools/fetch_assets.sh https://github.com/<owner>/<repo>/releases/download/<tag>/`
2. Offline app (the product): `./gradlew testOfflineDebugUnitTest installOfflineDebug`
3. Online app (optional, downloads its language files and a signed scam-number list; the only build with the internet permission):
   `./gradlew assembleOnlineDebug -PmodelBaseUrl=https://github.com/<owner>/<repo>/releases/download/<tag>/`
4. Language models (Gemma 1B, Omnilingual) are separate files: import `callguard-models.zip` in the app, or use the online build's download. `tools/make_model_pack.sh` builds the zip.

## Layout
`app/src/main/java/com/callguard/core` pure-Kotlin logic (detection, risk, languages, policies; all unit-tested) · `asr`, `gemma`, `alert`, `caller`, `power` Android layers · `ui` screens · `tools` eval and packaging scripts · `eval` replay clips.

## Licences and third-party models
This repository has no licence file yet, so all rights are reserved until one is added. The app uses sherpa-onnx (Apache-2.0), Whisper (MIT), silero VAD (MIT), MediaPipe (Apache-2.0), and the Gemma and Omnilingual models, which have their own terms: read them before redistributing those files.
