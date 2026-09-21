package com.callguard.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the "no internet" promise verifiable: the main manifest must never request network permissions. */
class PrivacyGuardTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun mainManifestHasNoNetworkPermission() {
        for (p in listOf("INTERNET", "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE", "CHANGE_NETWORK_STATE"))
            assertFalse("main manifest must not declare $p", "android.permission.$p" in manifest)
    }
    @Test fun manifestIsTheOneWeThinkItIs() = assertTrue("android.permission.RECORD_AUDIO" in manifest)

    @Test fun everyPrivacyStringExistsInAllLanguages() {
        for (k in listOf(Ui.PRIVACY, Ui.PRIVACY_BODY, Ui.ERASE_ALL, Ui.ERASE_CONFIRM, Ui.ERASE_DONE)) for (l in Lang.values())
            assertTrue("$k $l", UiStrings.get(k, l).isNotBlank())
    }
}
