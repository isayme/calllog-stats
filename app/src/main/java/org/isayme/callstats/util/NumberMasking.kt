package org.isayme.callstats.util

/**
 * Masks phone numbers before logging. Call log sentinel values are hidden framework
 * constants, so local copies are used here.
 */
object NumberMasking {
    private const val PRIVATE_NUMBER = "-1"
    private const val UNKNOWN_NUMBER = "-2"
    private const val PAYPHONE_NUMBER = "-3"

    fun mask(raw: String?): String = when {
        raw.isNullOrEmpty() -> "(empty)"
        raw == PRIVATE_NUMBER -> "(private)"
        raw == UNKNOWN_NUMBER -> "(unknown)"
        raw == PAYPHONE_NUMBER -> "(payphone)"
        else -> {
            val digits = raw.filter { it.isDigit() }
            if (digits.length <= 5) {
                "***"
            } else {
                "${digits.take(3)}***${digits.takeLast(2)}"
            }
        }
    }
}
