# CallGuard

On-device scam-call shield for Android. It listens to what is said on a call, understands English, Hindi and Telugu, and warns the person in their own language while the call is still happening. No audio or text ever leaves the phone — the product build carries no internet permission.

**Status: a prototype.** The pipeline works end to end on a Samsung Galaxy F17 with played-back audio. Whether a real cellular call's audio reaches the microphone reliably is not yet proven, and detection accuracy on real speech is not yet measured. See `PROJECT_SUMMARY.md` for the full, honest list of risks.

## The problem

Phone scams in India — fake bank/KYC calls, "digital arrest" threats, remote-access app installs, OTP theft — work because the victim is pressured in real time with nobody there to say "hang up." Older people, and people who speak Telugu, Hindi or another Indian language rather than English, are hit hardest. Existing protection (Truecaller, carrier spam filters) checks the *number*, not what is being *said*, and misses fresh or spoofed numbers. Cloud-based call analysis sends audio to a server, which many people will not accept.

## How it works

```
Phone rings ──> number checks (contacts, seen-before, prefix list) ──> heads-up notice before you answer
Call answered ─> mic (speaker on) ─> voice-activity detection ─> speech-to-text (English / Hindi / Telugu)
              ─> risk engine (rule patterns + on-device Gemma-3 1B + caller reputation)
              ─> spoken + on-screen warning, in the caller's language, while the call continues
Call ends ────> plain-language summary, one-tap recovery steps (1930, bank, uninstall app),
              family alert, block number — nothing above this line is ever written to disk
```

## What is different

| Existing | Limit | CallGuard |
|---|---|---|
| Truecaller, carrier spam filters | Number only; misses fresh or spoofed numbers | Reads the words too — a clean number does not get a pass |
| Google Pixel Scam Detection | On-device, but English-first, select devices | Works on ordinary phones; Hindi and Telugu supported natively |
| Cloud call-analysis apps | Audio sent to a server | No internet permission in the product build |
| Awareness campaigns | Read before the call, forgotten during it | Warns *during* the call, then helps with recovery after |

## Tech stack

Android (Kotlin), fully on-device: sherpa-onnx (Whisper and Omnilingual ASR, silero VAD), Gemma-3 1B via MediaPipe LLM Inference, `CallScreeningService` for caller ID. Full architecture, stack details and test coverage: `PROJECT_SUMMARY.md`.

## Read first
- `PROJECT_SUMMARY.md` — architecture, differentiation, critical gaps and risks (read before trusting any claim above)
- `IMPLEMENTATION_PLAN.md` — the plan and what has been verified
- `MODEL_DELIVERY.md` — how the language models reach a phone; `HACKATHON_CHECKLIST.md` — demo steps
- `PLAY_POLICY_NOTES.md` — distribution route and Play policy notes; `USABILITY_TEST.md` — setup test protocol

## Build
1. Get the three large build inputs (not in git): `./tools/fetch_assets.sh https://github.com/<owner>/<repo>/releases/download/<tag>/`
2. Offline app (the product): `./gradlew testOfflineDebugUnitTest installOfflineDebug`
3. Online app (optional; the only build with internet permission, downloads its language files and a signed scam-number list): `./gradlew assembleOnlineDebug -PmodelBaseUrl=https://github.com/<owner>/<repo>/releases/download/<tag>/`
4. Language models (Gemma 1B, Omnilingual) ship separately: import `callguard-models.zip` in the app, or use the online build's download. `tools/make_model_pack.sh` builds the zip.

## Layout
`app/src/main/java/com/callguard/core` pure-Kotlin logic (detection, risk, languages, policies; all unit-tested) · `asr`, `gemma`, `alert`, `caller`, `power` Android layers · `ui` screens · `tools` eval and packaging scripts · `eval` replay clips.

## Licences and third-party models
This repository has no licence file yet, so all rights are reserved until one is added. The app uses sherpa-onnx (Apache-2.0), Whisper (MIT), silero VAD (MIT), MediaPipe (Apache-2.0), and the Gemma and Omnilingual models, which have their own terms: read them before redistributing those files.
