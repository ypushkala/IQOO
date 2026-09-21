package com.callguard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {
    private val blocked = setOf("abc123")

    @Test fun onlyAnExplicitlyBlockedNumberIsRejected() {
        assertTrue(BlockPolicy.shouldReject(blocked, "abc123", "9876543210"))
        assertFalse(BlockPolicy.shouldReject(blocked, "other", "9876543210"))
        assertFalse(BlockPolicy.shouldReject(emptySet(), "abc123", "9876543210"))
    }
    @Test fun unknownOrHiddenCallersAreNeverRejected() {
        assertFalse(BlockPolicy.shouldReject(blocked, null, ""))
        assertFalse(BlockPolicy.shouldReject(blocked, "abc123", ""))
    }
    @Test fun emergencyAndHelplineNumbersAreNeverBlockedEvenIfListed() {
        for (n in listOf("112", "100", "108", "1930", "+112")) assertFalse(n, BlockPolicy.shouldReject(blocked, "abc123", n))
        assertFalse(BlockPolicy.canOfferBlock("abc123", "112"))
    }
    @Test fun blockIsOfferedOnlyWhenTheCallerIsKnown() {
        assertTrue(BlockPolicy.canOfferBlock("abc123", null))
        assertFalse(BlockPolicy.canOfferBlock(null, null))
    }
    @Test fun blockStringsExistInAllLanguages() {
        for (l in Lang.values()) for (k in listOf(Ui.BLOCK_NUMBER, Ui.UNBLOCK_NUMBER, Ui.BLOCK_NOTE, Ui.BLOCK_CLEAR)) assertTrue(UiStrings.get(k, l).isNotBlank())
        for (l in Lang.values()) assertTrue(UiStrings.fmt(Ui.BLOCK_LIST_FMT, l, 3).contains("3"))
    }
}
