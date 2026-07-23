package com.togethertrip.gateway.global.logging

object SensitiveDataMasker {
    private const val MASK = "***"

    private val keyValuePatterns = listOf(
        Regex("(?i)(password|passwd|pwd|token|accessToken|refreshToken|authorization|cookie|secret|apiKey)=([^&\\s]+)"),
        Regex("(?i)(\"(?:password|passwd|pwd|token|accessToken|refreshToken|authorization|cookie|secret|apiKey)\"\\s*:\\s*\")([^\"]+)(\")"),
    )
    private val bearerTokenPattern = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val phonePattern = Regex("(?<!\\d)(?:\\+8210\\d{8}|010[- ]?\\d{4}[- ]?\\d{4})(?!\\d)")
    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun mask(value: String): String {
        var masked = value

        keyValuePatterns.forEach { pattern ->
            masked = pattern.replace(masked) { match ->
                if (match.groupValues.size == 4) {
                    "${match.groupValues[1]}$MASK${match.groupValues[3]}"
                } else {
                    "${match.groupValues[1]}=$MASK"
                }
            }
        }

        masked = bearerTokenPattern.replace(masked, "Bearer $MASK")
        masked = phonePattern.replace(masked, MASK)
        masked = emailPattern.replace(masked, MASK)

        return masked
    }
}
