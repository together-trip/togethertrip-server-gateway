package com.togethertrip.gateway

import com.togethertrip.gateway.global.security.GatewayCorsProperties
import com.togethertrip.gateway.global.security.GatewayRateLimitProperties
import com.togethertrip.gateway.global.security.GatewayTransportSecurityProperties
import com.togethertrip.gateway.global.security.GatewayTrustedProxyProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("prod")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "MAIN_SERVICE_URL=http://127.0.0.1:18081",
        "NOTIFICATION_SERVICE_URL=http://127.0.0.1:18082",
        "CHAT_SERVICE_URL=http://127.0.0.1:18083",
        "GATEWAY_CORS_ALLOWED_ORIGINS=https://app.togethertrip.co.kr,https://admin.togethertrip.co.kr",
        "GATEWAY_TRUSTED_PROXY_CIDRS=10.0.0.0/8,2001:db8::/32",
        "JWT_SECRET=production-test-secret-key-at-least-32-bytes",
    ],
)
class GatewayProductionSecurityConfigurationTest {

    @Autowired
    private lateinit var corsProperties: GatewayCorsProperties

    @Autowired
    private lateinit var trustedProxyProperties: GatewayTrustedProxyProperties

    @Autowired
    private lateinit var transportSecurityProperties: GatewayTransportSecurityProperties

    @Autowired
    private lateinit var rateLimitProperties: GatewayRateLimitProperties

    @Test
    fun `운영 환경변수는 보안 설정 목록과 기본값으로 바인딩된다`() {
        assertEquals(
            listOf("https://app.togethertrip.co.kr", "https://admin.togethertrip.co.kr"),
            corsProperties.allowedOrigins,
        )
        assertEquals(true, corsProperties.requireHttpsOrigins)
        assertEquals(listOf("10.0.0.0/8", "2001:db8::/32"), trustedProxyProperties.cidrs)
        assertEquals(true, transportSecurityProperties.requireHttps)
        assertEquals(true, rateLimitProperties.enabled)
        assertEquals(20, rateLimitProperties.loginRequestsPerMinute)
        assertEquals(5, rateLimitProperties.reportRequestsPerMinute)
    }
}
