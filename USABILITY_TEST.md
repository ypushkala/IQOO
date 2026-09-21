# Setup usability test (3 to 5 people)

**Goal:** a person who is not technical gets from "just installed" to "protected" in **under 3 minutes and about 8 taps**, without help.

## Who
3 to 5 people, ideally older adults or people who rarely change phone settings; at least 2 who prefer Telugu (or Hindi). Do not pick colleagues or other developers.

## Before each person
1. Fresh install, or Settings > Erase all CallGuard data, then remove the app's permissions. The model pack (`callguard-models.zip`) should already be on the phone (Downloads) or the phone should be on Wi-Fi with the online build.
2. Phone screen on, CallGuard **not** opened. Note the phone model.

## What you say (read it out, then stay silent)
"This app warns you when a phone call looks like a scam. Please get it ready to use. I can't help, but tell me out loud what you are thinking."

## What to record
| | |
|---|---|
| Time from opening the app to the "You are ready" screen | Settings > "Last setup: m:ss, N taps" shows it automatically (tick means target met) |
| Where they hesitated or read something twice | note the step |
| Any word they did not understand | write the exact word |
| Any step they could not finish | which one, and what they tried |
| Did they understand the practice call? | ask: "What would you do if you heard that on a real call?" |
| Did they choose the right language on their own? | yes / no |

## After
Ask three questions: "What was the most confusing part?", "Would you trust this on your mother's phone?", "What would you change first?"

## Scoring per person
- **Pass:** finished alone, under 3:00, at most 8 in-app taps, answered the practice-call question correctly.
- **Near:** finished alone but over time or taps.
- **Fail:** needed help or gave up.

Fix the most common stumbling block first, then re-test with two new people. The in-app tap count does not include the phone's own permission pop-ups (each permission and the role dialog add their own taps), so also count those by watching.
