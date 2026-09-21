package com.callguard.core

/**
 * User-initiated blocking of future calls. Nothing is blocked unless the user tapped "Block this number" for that exact
 * number. Emergency and helpline numbers are never blocked, whatever the list says.
 */
object BlockPolicy {
    private val neverBlock = setOf("112", "100", "101", "102", "108", "181", "1091", "1098", "1930", "911", "999")

    fun shouldReject(blockedHashes: Set<String>, numberHash: String?, digits: String): Boolean {
        if (numberHash == null || digits.isEmpty()) return false
        if (digits.removePrefix("+") in neverBlock) return false
        return numberHash in blockedHashes
    }

    /** Whether the summary card should offer "Block this number" (needs a known caller). */
    fun canOfferBlock(numberHash: String?, digits: String?): Boolean =
        numberHash != null && !(digits != null && digits.removePrefix("+") in neverBlock)
}
