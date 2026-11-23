package com.example.splendit.domain

/**
 * Countries offered by the phone field. [digits] is the length of the national number without
 * the trunk prefix; [trunkZero] countries write a leading 0 at home that is dropped after the
 * country code (Swedish 070… becomes +4670…).
 */
enum class Country(
    val flag: String,
    val label: String,
    val dialCode: String,
    val digits: IntRange,
    val trunkZero: Boolean = false
) {
    DK("🇩🇰", "Denmark", "+45", 8..8),
    SE("🇸🇪", "Sweden", "+46", 7..9, trunkZero = true),
    NO("🇳🇴", "Norway", "+47", 8..8),
    DE("🇩🇪", "Germany", "+49", 10..11, trunkZero = true),
    GB("🇬🇧", "United Kingdom", "+44", 10..10, trunkZero = true);

    companion object {
        val DEFAULT = DK
    }
}

/** Phone numbers are stored as E.164 (`+4512345678`) so they can be looked up exactly. */
object Phone {
    fun nationalDigits(country: Country, input: String): String {
        val digits = input.filter(Char::isDigit)
        return if (country.trunkZero && digits.startsWith("0")) digits.drop(1) else digits
    }

    fun isValid(country: Country, input: String): Boolean =
        nationalDigits(country, input).length in country.digits

    fun e164(country: Country, input: String): String =
        country.dialCode + nationalDigits(country, input)

    /**
     * Normalises what someone typed to find a member: an email is lower-cased; a number with
     * a leading `+` keeps its country code; anything else is read as a [default] number.
     */
    fun lookupKey(input: String, default: Country = Country.DEFAULT): String? {
        val text = input.trim()
        if ('@' in text) return text.lowercase()
        if (text.startsWith("+")) {
            val digits = text.filter(Char::isDigit)
            return if (digits.length in 7..15) "+$digits" else null
        }
        return if (isValid(default, text)) e164(default, text) else null
    }
}
