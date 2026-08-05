package com.togethertrip.gateway.global.security

import com.togethertrip.gateway.global.security.jwt.UserRole
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalTestIdentityTest {

    @Test
    fun `user identity 토큰에서 양수 사용자 ID를 파싱한다`() {
        assertEquals(
            LocalTestIdentity(userId = 123L, role = UserRole.USER),
            LocalTestIdentity.parse("local-test:user:123"),
        )
    }

    @Test
    fun `admin identity 토큰에서 관리자 역할을 파싱한다`() {
        assertEquals(
            LocalTestIdentity(userId = 7L, role = UserRole.ADMIN),
            LocalTestIdentity.parse("local-test:admin:7"),
        )
    }

    @Test
    fun `모호하거나 유효하지 않은 identity 토큰은 거부한다`() {
        listOf(
            "local-test:verified:swagger",
            "local-test:user:",
            "local-test:user:0",
            "local-test:user:-1",
            "local-test:user:not-a-number",
            "local-test:owner:1",
            "jwt-token",
        ).forEach { token -> assertNull(LocalTestIdentity.parse(token), token) }
    }
}
