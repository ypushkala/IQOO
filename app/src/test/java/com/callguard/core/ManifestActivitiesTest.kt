package com.callguard.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** A screen that is not declared in the manifest crashes the app the moment it is opened; this catches that at build time. */
class ManifestActivitiesTest {
    @Test fun everyActivityClassIsDeclared() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val classes = File("src/main/java/com/callguard/ui").listFiles { f -> f.name.endsWith("Activity.kt") }!!.map { it.name.removeSuffix(".kt") }
        assertTrue(classes.isNotEmpty())
        for (c in classes) assertTrue("$c is missing from AndroidManifest.xml", ".ui.$c" in manifest)
    }
}
