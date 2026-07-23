package com.togethertrip.gateway.global.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SensitiveDataMaskerTest {

    @Test
    fun `토큰과 쿠키를 마스킹한다`() {
        val masked = SensitiveDataMasker.mask("token=secret&cookie=session")

        assertFalse(masked.contains("secret"))
        assertFalse(masked.contains("session"))
        assertContains(masked, "***")
    }

    @Test
    fun `Authorization 전화번호 이메일을 마스킹한다`() {
        val masked = SensitiveDataMasker.mask(
            "Authorization: Bearer abc.def phone=010-1234-5678 user@example.com",
        )

        assertFalse(masked.contains("abc.def"))
        assertFalse(masked.contains("010-1234-5678"))
        assertFalse(masked.contains("user@example.com"))
    }
}
