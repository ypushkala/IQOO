package com.callguard.core

/** The pretend scammer line for the practice call. A test checks that the real detector rates each one HIGH. */
object PracticeScript {
    fun line(lang: Lang) = when (lang) {
        Lang.EN -> "This is your bank. Please tell me your OTP immediately."
        Lang.HI -> "आपका ओटीपी बताइए, यह बहुत ज़रूरी है।"
        Lang.TE -> "మీ otp చెప్పండి ఇది చాలా అధ్యవసరం"
    }
}
