# CallGuard: project summary (2026-09-22)

## 1. Problem
Phone scams in India (fake bank/KYC calls, "digital arrest", remote-access app installs, OTP theft) work because the victim is pressured in real time and nobody is there to say "hang up". Older and less technical people, and people who speak Telugu, Hindi or another Indian language rather than English, are hit hardest. Existing protection mostly checks the *number* (spam lists) and cannot tell what is being *said*. Cloud-based options send call audio to a server, which many people will not accept.

CallGuard's goal: a shield that **listens to what is said on the call, on the phone itself, and warns the person in their own language while the call is still happening**, with no audio ever leaving the device.

## 2. Tech stack
| Layer | Choice |
|---|---|
| Platform | Android (Kotlin), minSdk 26, targetSdk 35, tested on Samsung Galaxy F17, Android 16 |
| Audio | `AudioRecord` on the MIC source, 16 kHz mono, 30 s ring buffer, speakerphone during analysed calls |
| Voice activity | silero VAD (sherpa-onnx) |
| Speech to text | sherpa-onnx: Whisper base multilingual (English, translation) and Omnilingual CTC (Hindi, Telugu and other Indian languages) |
| Understanding | Rule engine (tactic-tagged patterns on normalised text, Hindi/Hinglish/Telugu normalisers, negation/"warning" detection) + Gemma-3 1B on device (MediaPipe LLM Inference) + caller-number reputation, fused in a risk engine |
| Warnings | On-screen, vibration, Android TextToSpeech (English/Hindi/Telugu), short in normal mode, long in accessibility mode |
| Call awareness | `CallScreeningService` (caller-ID role, observe-only except numbers the user blocked), TelephonyCallback |
| Storage | Private SharedPreferences: settings, salted hashes of numbers, feedback, block list. No audio, no transcripts |
| Builds | `offline` flavour (no internet permission, the product), `online` flavour (optional signed scam-number list download), R8-shrunk release, arm64 only (220 MB) |
| Tests | 350+ JUnit tests on a pure-Kotlin core, on-device replay eval (`tools/eval.py`), capture probe |

## 3. Architecture (one call, start to finish)
```
Phone rings ──> CallerIdService: number checks (contacts, seen-before, prefix list, verification)
                 └─ suspicious? heads-up notification (localised) BEFORE you answer
Call answered ─> ListenPolicy: unknown numbers only? ─> CallGuardService (foreground, mic type)
                 AudioRecord(MIC) ─> ring buffer ─> VAD ─> ASR (Whisper / Omnilingual, language routing)
                 ─> SelfSpeechFilter (ignore our own warning) ─> rolling transcript
                 ─> RiskEngine = rules + Gemma + number reputation  ──> AlertDebouncer
                 ─> Alerter: screen + vibration + short spoken warning (call language) [+ optional family SMS]
Call ends ─────> CallSummary (in memory) ─> notification + summary card:
                 feedback, block number, "I already shared something" checklist, family composer, report
```
Everything above the summary is in memory; nothing is written to disk except the small items listed under Storage. The pure-Kotlin `core/` package holds all decision logic so it can be unit-tested without a phone.

## 4. What is different from existing solutions
| Existing | Limit | CallGuard |
|---|---|---|
| Truecaller, Google Phone spam warnings, carrier spam filters | Judge the **number** only; miss fresh numbers and spoofed ones; no view of the conversation | Judges the **words** as well as the number; a scam from a clean number is still caught |
| Google Pixel "Scam Detection" | On-device, but Pixel/select regions and English-first; not for Indian languages | Works on ordinary phones; Hindi and Telugu supported natively (Omnilingual), not through translation |
| Cloud call-analysis apps | Send call audio to servers | No internet permission in the product build; audio never stored or sent |
| Bank/government awareness campaigns | Read before the call, forgotten during it | Speaks up **during** the call in the caller's language |
| All of the above | End at the warning | After the call: plain-language summary, one-tap "I already shared something" recovery steps (1930, bank, uninstall app), family alert, block number |
Also: transparency (every family alert is shown before it is enabled and logged), accessibility mode (large text, louder, repeated warnings), user-controlled data (feedback stays on the phone, one-tap erase).

**Honest caveat:** the main differentiator (hearing the conversation) depends on getting the other person's audio, and that is not solved on ordinary phones (see section 6).

## 5. What could improve: UI and navigation
See the separate answer in the chat; short version:
- The home screen is a stack of ~12 buttons of equal weight (setup, start, caller-ID, languages, settings, accessibility, family x3, test, stop, summary actions). Nothing says "this is the one thing to do".
- Fix in three moves: (1) **one status + one action** on Home; (2) a **Settings hub** that absorbs Languages, Protection, Accessibility, Family, Blocked numbers, Models, Privacy; (3) **hide developer controls** (Test capture, Stop, technical details) outside debug builds. Then add **History** (past calls) and simplify the post-call card to one urgent action.
- A "helper setup" path so a family member can set it up for an elderly parent.
- Usability test with 5 real users before polishing visuals.

## 6. Critical gaps and risks
1. **Real-call audio capture is unproven (the biggest risk).** In our earlier tests on the F17 the microphone recorded silence during real cellular calls; speaker-based tests (call audio played aloud near the phone) worked. Whether a real call on speaker reaches the microphone well enough is still to be confirmed, and other brands may behave differently. Until the second-phone experiments finish, the core promise is unproven. Fallbacks: OEM/system integration, or repositioning as post-call analysis plus number checks.
2. **Speakerphone requirement.** Forcing speaker means the caller is heard by everyone nearby and the phone must be away from the ear, which many users will not accept. Needs a real-user reaction, not just a working demo.
3. **Detection quality on real speech is unmeasured.** The evaluation clips are marked as tuning ('dev') data, so they show regressions, not accuracy. Need held-out real Telugu and Hindi conversations, accents, background noise, false alarms per hour on ordinary talk.
4. **Time to warn.** A scammer can get an OTP in the first 20 seconds. We have not measured how long from spoken phrase to warning on a real call, nor whether the warning arrives before the damage.
5. **Legal and consent.** Analysing a call is monitoring another person's speech. Indian call-monitoring and data-protection rules (and other countries') need a lawyer's review; the disclosure beep is a mitigation, not a clearance. Also wording: "LOW risk" must never read as "safe".
6. **Distribution of large models.** Gemma and Omnilingual (~0.9 GB) are not in the APK; today they arrive by adb or in-app file import. Non-technical users cannot do this. Needs an installer pack, a Play asset pack, or preloading by a phone maker.
7. **Coverage gaps.** WhatsApp / video-call "digital arrest" scams, SMS and link scams, and screen-sharing sessions are outside call screening and the phone-call audio path.
8. **Scam scripts change; our rules do not update.** The signed update only covers number prefixes. Keywords/patterns are compiled into the app. Needs a signed rules-update path (the online flavour could carry it) and a way to learn from misses.
9. **Phone variety, battery, heat.** One mid-range phone was tested. Low-RAM phones may not run Gemma plus two ASR models; thermal throttling reached SEVERE in testing; battery was 5% of weekly use but not measured unplugged. Aggressive battery managers (Xiaomi, vivo, Oppo) can kill the service.
10. **No field learning loop.** With no internet, we cannot see failures. Consider an opt-in, user-reviewed "share this report" export.
11. **Abuse and safety.** Notification and block-list features must not be usable to hide real calls; auto-SMS must never fire on demo audio played near the phone (consent screen and once-per-call limit help). Warning TTS voices for Telugu/Hindi may not be installed on every phone (the app falls back to English; needs a visible cue).
12. **Business and ownership.** No decision yet on who ships it (open source, an NGO, a phone maker, a bank/telecom partner) or how it is funded and maintained.

## 7. Pending work that needs the second phone
See the list in the chat and in `IMPLEMENTATION_PLAN.md` (wave 2).
