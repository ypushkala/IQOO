package com.callguard.core

data class FamilyContact(val name: String?, val number: String) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: number
}

/** The people who get a family alert (at most [MAX]). Stored on this phone only. */
object FamilyContacts {
    const val MAX = 3

    private fun digits(n: String) = n.filter { it.isDigit() }.takeLast(10)

    fun add(list: List<FamilyContact>, c: FamilyContact): List<FamilyContact> {
        if (c.number.isBlank() || list.any { digits(it.number) == digits(c.number) }) return list
        return (list + c).take(MAX)
    }
    fun remove(list: List<FamilyContact>, c: FamilyContact) = list.filter { it != c }

    fun encode(list: List<FamilyContact>) = list.joinToString("\n") { "${(it.name ?: "").replace(Regex("[\\n|]"), " ")}|${it.number.replace(Regex("[\\n|]"), "")}" }
    fun decode(s: String): List<FamilyContact> = s.split('\n').mapNotNull { line ->
        val p = line.split('|', limit = 2)
        if (p.size == 2 && p[1].isNotBlank()) FamilyContact(p[0].ifBlank { null }, p[1]) else null
    }.take(MAX)
}
