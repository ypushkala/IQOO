# W11.6: Distribution route and Google Play policy notes

Written 2026-09-22. Rows marked **[checked]** come from the Google pages linked at the bottom, read on that date. Rows marked **[to verify]** are from memory or judgement and must be confirmed in the current Play Console before relying on them. Play policy changes often; re-read before submitting.

## Recommendation

| Route | Verdict |
|---|---|
| **Sideload (direct APK, own site or F-Droid-style)** | **Use for the prototype and first real users.** Nothing below blocks it. The whole feature set, including automatic family SMS, works. Users must allow installs from unknown sources, which is a real barrier for the older, less technical users this app is for. |
| **Phone-maker bundling (OEM)** | **Best long-term route for the audio problem.** The biggest risk is not policy, it is that the microphone is silent during real cellular calls on the F17. Only a phone maker (or a system app with privileged call-audio access) can fix that properly. Worth a pitch once the real-call experiments (W1) are in. |
| **Google Play** | **Possible only with a reduced build, and only after the real-call audio question is answered.** A build for Play would have no SEND_SMS and no online updater, and would need the declarations listed below. Not worth doing before we know the product works on real calls. |

## What blocks or shapes a Play release

| Item | What Play requires | Our situation | Action |
|---|---|---|---|
| **SEND_SMS** | **[checked]** SMS and Call Log permissions (SEND_SMS is on the list) may be used only when the app is the default SMS/Phone/Assistant handler or fits a listed exception, and must be declared in the Permissions Declaration Form. Apps that do not meet this can be removed. | Automatic family alerts (W7.2) use SEND_SMS but CallGuard is not the default SMS app. The exception list covers "anti-SMS phishing protection" and "caller ID and spam detection"; sending a text to a family member does not clearly fit either. **[to verify]** a declaration would likely be refused. | **Play build must not declare SEND_SMS.** Keep the one-tap composer (opens the messages app; needs no permission). Automatic SMS stays in the sideload/OEM build. |
| **Microphone foreground service** | **[checked]** Apps targeting Android 14+ must declare each foreground service type on the App content page in Play Console, with a description of the feature, the user impact if it is interrupted, and a link to a video demonstrating it. The microphone type needs the RECORD_AUDIO runtime permission. | We use `foregroundServiceType="microphone"` while a call is being analysed. | Prepare the declaration text and a short screen-recorded demo (a scam-call test with the warning). Describe it honestly: audio is analysed on the phone and never stored or sent. |
| **Call-screening role** | **[to verify]** No special declaration is known for the call-screening role, but reviewers look closely at apps that handle calls. | CallerIdService reads the incoming number and only rejects numbers the user blocked. | Explain in the store listing what it does and that nothing is sent. |
| **AccessibilityService** | **[checked]** Only services designed to help people with disabilities may declare themselves accessibility tools; others need a prominent in-app disclosure and consent. | The accessibility service exists only in debug builds (experiment E3). | Keep it out of every release build. "Accessibility mode" in our UI is a display setting, not the API. |
| **Personal data / Data safety form** | **[to verify]** Play requires a privacy policy URL and a Data safety declaration. Data that is processed only on the device and never leaves it is generally declared as not collected. | Audio and transcript stay in memory; only settings, hashed numbers and feedback are stored, on the phone. The privacy text is already in the app (W9.3). | Publish that text as a policy page. Re-check the wording of "collected" in the form. |
| **Call monitoring / consent** | **[to verify]** Play itself has few call-audio rules for this use, but local law applies (W9.2 legal review) and Play requires apps to comply with local laws. | Disclosure beep is on by default (W9.1). | Do the legal review before any public release, on any route. |
| **Battery-optimisation exemption** | **[to verify]** Play restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to apps whose core function needs it. | We declare it so protection is not killed while idle. | Justify in the declaration, or remove the permission and send users to the settings page instead. |
| **Download size** | **[to verify]** Play's base app limit is about 200 MB (larger assets go into Play Asset Delivery packs). | Release APK is 220 MB (Whisper base 160 MB is the bulk). Gemma and the Hindi/Telugu model are imported by the user, not bundled. | For Play, put the Whisper model in an install-time asset pack. Keep Gemma/Omnilingual as user imports or an on-demand pack. |
| **Optional network build** | **[to verify]** Downloading a data list is normal. | The online flavour is a separate app id (`com.callguard.online`). | Do not upload it to Play; it is for sideloaders. |
| **Target API level and signing** | **[to verify]** Play requires a recent target SDK and an app bundle signed with Play App Signing. | targetSdk 35, no release keystore yet, APK output only. | Set up a release keystore and build an AAB when a Play route is chosen. |

## Build-variant plan (only if Play becomes the route)

1. Add a `play` flavour: offline behaviour, **no SEND_SMS in the manifest**, the automatic-SMS toggle hidden, no updater. The current `offline` flavour stays as the full sideload build.
2. Move the Whisper model into an asset pack.
3. Release keystore, AAB output.
4. Foreground-service declaration, Data safety form, privacy page, store listing text that says plainly what is and is not analysed.

None of this is built yet. It is worth doing only after the real-call audio result (W1) says the product works, because that answer decides between "Play/sideload app" and "OEM feature".

## Sources
- SMS and Call Log permissions policy: https://support.google.com/googleplay/android-developer/answer/10208820
- AccessibilityService API policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Foreground service and full-screen intent requirements (Play Console): https://support.google.com/googleplay/android-developer/answer/13392821
- Android foreground service declaration: https://developer.android.com/develop/background-work/services/fgs/declare
