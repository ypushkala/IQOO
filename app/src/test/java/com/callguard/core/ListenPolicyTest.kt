package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class ListenPolicyTest {
    private val rep = NumberReputation()
    private fun caller(contacts: Boolean?, number: String? = "+919876543210") = rep.assess(CallerContext(number, inContacts = contacts))

    @Test fun savedContactsAreNotListenedToInTheDefaultMode() {
        val d = ListenPolicy.decide(AnalyseScope.UNKNOWN_ONLY, caller(true))
        assertFalse(d.listen); assertEquals("saved contact", d.reason)
    }
    @Test fun unknownNumbersAreListenedTo() {
        assertTrue(ListenPolicy.decide(AnalyseScope.UNKNOWN_ONLY, caller(false)).listen)
        assertTrue(ListenPolicy.decide(AnalyseScope.UNKNOWN_ONLY, caller(null)).listen) // contacts permission missing: cannot tell, so listen
        assertTrue(ListenPolicy.decide(AnalyseScope.UNKNOWN_ONLY, caller(false, number = null)).listen) // withheld
    }
    @Test fun noInformationMeansListen() {
        val d = ListenPolicy.decide(AnalyseScope.UNKNOWN_ONLY, null)
        assertTrue(d.listen); assertEquals("caller not known", d.reason)
    }
    @Test fun allModeAlwaysListens() {
        for (c in listOf(caller(true), caller(false), null)) assertTrue(ListenPolicy.decide(AnalyseScope.ALL, c).listen)
    }
    @Test fun defaultIsTheRecommendedMode() = assertEquals(AnalyseScope.UNKNOWN_ONLY, AnalyseScope.values().first())
    @Test fun settingsStringsExistInEveryLanguage() {
        for (l in Lang.values()) for (s in AnalyseScope.values()) assertTrue(UiStrings.scopeOption(s, l).isNotBlank())
    }
}
