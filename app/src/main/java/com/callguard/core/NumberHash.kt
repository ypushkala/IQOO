package com.callguard.core

import java.security.MessageDigest

/** Salted one-way hash so CallGuard can remember "seen this number" without keeping the number itself. */
object NumberHash {
    fun hash(salt: String, digits: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$salt:$digits".toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }
}
