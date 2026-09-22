package com.callguard.core

/** The pretend scammer line for the practice call. A test checks that the real detector rates each one HIGH. */
object PracticeScript {
    /** Kept for the original one-line demo (and older tests): the OTP scenario's line, same wording as PracticeScenarios. */
    fun line(lang: Lang) = PracticeScenarios.byId("otp").line(lang)
}
