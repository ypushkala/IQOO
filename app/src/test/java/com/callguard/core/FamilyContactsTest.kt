package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyContactsTest {
    private val a = FamilyContact("Amma", "+91 98765 43210")
    private val b = FamilyContact("Anna", "9123456789")
    private val c = FamilyContact(null, "9000000001")
    private val d = FamilyContact("Extra", "9000000002")

    @Test fun addsUpToThreeAndIgnoresTheSameNumberTwice() {
        var l = FamilyContacts.add(emptyList(), a)
        l = FamilyContacts.add(l, FamilyContact("Amma again", "9876543210")) // same number, different format
        assertEquals(1, l.size)
        l = FamilyContacts.add(FamilyContacts.add(FamilyContacts.add(l, b), c), d)
        assertEquals(3, l.size); assertEquals(listOf(a, b, c), l)
    }
    @Test fun removeAndRoundTrip() {
        val l = listOf(a, b, c)
        assertEquals(listOf(a, c), FamilyContacts.remove(l, b))
        assertEquals(l, FamilyContacts.decode(FamilyContacts.encode(l)))
        assertEquals(emptyList<FamilyContact>(), FamilyContacts.decode(""))
    }
    @Test fun labelFallsBackToTheNumber() = assertEquals("9000000001", c.label)
}
