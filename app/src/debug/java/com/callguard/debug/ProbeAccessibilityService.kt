package com.callguard.debug

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Deliberately inert (experiment E3). Enabling it is the only thing it does. */
class ProbeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
