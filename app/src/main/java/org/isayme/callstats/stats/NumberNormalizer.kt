package org.isayme.callstats.stats

/**
 * Normalizes numbers for call log matching.
 *
 * Default mode strips non-digits, drops the `0086`/`86` country code when the remaining
 * number is 10-11 digits, and truncates dialing extensions. Strict mode only strips
 * non-digits. Numbers shorter than 7 digits are compared exactly so service numbers such
 * as 110 or 10086 are never mistaken for country-code-prefixed numbers.
 */
object NumberNormalizer {
    private const val MIN_COUNTRY_CODE_LENGTH = 7
    private const val COUNTRY_CODE = "86"
    private const val INTERNATIONAL_PREFIX = "0086"
    private const val MIN_NATIONAL_NUMBER_LENGTH = 10
    private const val MAX_NATIONAL_NUMBER_LENGTH = 11

    private const val PRIVATE_NUMBER = "-1"
    private const val UNKNOWN_NUMBER = "-2"
    private const val PAYPHONE_NUMBER = "-3"

    fun normalize(raw: String?, normalizeCountryCode: Boolean): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed in HIDDEN_NUMBERS) {
            return null
        }

        // Extensions and pause/wait separators are not part of the dialed number.
        val base = trimmed.takeWhile { it != ',' && it != ';' }
        val digits = base.filter { it.isDigit() }
        if (digits.isEmpty()) {
            return null
        }
        if (!normalizeCountryCode || digits.length < MIN_COUNTRY_CODE_LENGTH) {
            return digits
        }

        if (digits.startsWith(INTERNATIONAL_PREFIX) &&
            digits.length - INTERNATIONAL_PREFIX.length in
            MIN_NATIONAL_NUMBER_LENGTH..MAX_NATIONAL_NUMBER_LENGTH
        ) {
            return digits.removePrefix(INTERNATIONAL_PREFIX)
        }
        if (digits.startsWith(COUNTRY_CODE) &&
            digits.length - COUNTRY_CODE.length in
            MIN_NATIONAL_NUMBER_LENGTH..MAX_NATIONAL_NUMBER_LENGTH
        ) {
            return digits.removePrefix(COUNTRY_CODE)
        }
        return digits
    }

    private val HIDDEN_NUMBERS = setOf(PRIVATE_NUMBER, UNKNOWN_NUMBER, PAYPHONE_NUMBER)
}
