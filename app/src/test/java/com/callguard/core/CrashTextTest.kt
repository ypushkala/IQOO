package com.callguard.core

import com.callguard.CallGuardApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashTextTest {
    @Test fun crashLineHasTheClassAndNoMessage() {
        val e = IllegalStateException("your OTP is 123456")
        val line = CallGuardApp.describe(e)
        assertTrue(line.startsWith("IllegalStateException"))
        assertFalse("123456" in line); assertFalse("OTP" in line)
    }
    @Test fun frameLocationsAreKept() {
        val line = CallGuardApp.describe(RuntimeException("x"))
        assertTrue("CrashTextTest" in line)
    }
}
