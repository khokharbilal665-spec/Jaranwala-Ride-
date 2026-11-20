package com.example.util

/**
 * Validation result for Pakistani mobile numbers (+92).
 */
data class PakistanPhoneValidationResult(
    val isValid: Boolean,
    val normalizedE164: String, // e.g. +923001234567
    val localFormatted: String,  // e.g. 0300-1234567
    val operatorName: String,    // Jazz, Zong, Telenor, Ufone, Warid, SCOM
    val errorMessage: String? = null,
    val urduErrorMessage: String? = null
)

object PakistanPhoneValidator {

    /**
     * Validates Pakistani mobile phone numbers.
     * Accepted formats:
     * - 03001234567 (11 digits starting with 03)
     * - 0300-1234567
     * - 3001234567 (10 digits starting with 3)
     * - +923001234567
     * - 00923001234567
     */
    fun validate(rawInput: String): PakistanPhoneValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return PakistanPhoneValidationResult(
                isValid = false,
                normalizedE164 = "",
                localFormatted = "",
                operatorName = "Unknown",
                errorMessage = "Please enter your mobile number",
                urduErrorMessage = "براہِ کرم اپنا موبائل نمبر درج کریں"
            )
        }

        // Strip spaces, dashes, parentheses
        var clean = trimmed.replace(Regex("[\\s\\-\\(\\)]"), "")

        // Handle prefixes
        if (clean.startsWith("+92")) {
            clean = clean.substring(3)
        } else if (clean.startsWith("0092")) {
            clean = clean.substring(4)
        } else if (clean.startsWith("92") && clean.length == 12) {
            clean = clean.substring(2)
        }

        // If it starts with 0, drop the leading 0 for normalization
        if (clean.startsWith("0")) {
            clean = clean.substring(1)
        }

        // Now `clean` should be 10 digits starting with '3'
        if (clean.length != 10) {
            return PakistanPhoneValidationResult(
                isValid = false,
                normalizedE164 = "",
                localFormatted = "",
                operatorName = "Unknown",
                errorMessage = "Pakistani numbers must be 11 digits (e.g. 0300-1234567)",
                urduErrorMessage = "پاکستانی نمبر ۱۱ ہندسوں پر مشتمل ہونا چاہیے (مثلاً: 0300-1234567)"
            )
        }

        if (!clean.startsWith("3")) {
            return PakistanPhoneValidationResult(
                isValid = false,
                normalizedE164 = "",
                localFormatted = "",
                operatorName = "Unknown",
                errorMessage = "Pakistani mobile numbers must begin with 03 (e.g. 0300)",
                urduErrorMessage = "موبائل نمبر 03 سے شروع ہونا لازمی ہے"
            )
        }

        // Check 3-digit operator code (clean starts with '3', e.g., "300", "319", "326", "339", "345", "355", "370")
        val operatorCode = clean.substring(0, 3)
        val operatorName = when (operatorCode) {
            in "300".."309" -> "Jazz / Mobilink"
            in "310".."319" -> "Zong 4G"
            in "320".."329" -> "Warid / Jazz"
            in "330".."339" -> "Ufone 4G"
            in "340".."349" -> "Telenor 4G"
            "355" -> "SCOM"
            "370" -> "ONIC Pakistan"
            else -> {
                return PakistanPhoneValidationResult(
                    isValid = false,
                    normalizedE164 = "",
                    localFormatted = "",
                    operatorName = "Invalid Operator",
                    errorMessage = "Unrecognized Pakistan mobile operator code '0$operatorCode'",
                    urduErrorMessage = "نامعلوم پاکستانی نیٹ ورک کوڈ '0$operatorCode'"
                )
            }
        }

        val e164 = "+92$clean"
        val formattedLocal = "0${clean.substring(0, 3)}-${clean.substring(3)}"

        return PakistanPhoneValidationResult(
            isValid = true,
            normalizedE164 = e164,
            localFormatted = formattedLocal,
            operatorName = operatorName
        )
    }
}
