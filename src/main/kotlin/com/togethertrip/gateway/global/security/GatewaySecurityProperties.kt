package com.togethertrip.gateway.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "gateway.security")
class GatewaySecurityProperties {
    var publicPaths: List<String> = listOf(
        "/api/auth/oauth/kakao",
        "/api/auth/oauth/apple",
        "/api/auth/refresh",
        "/api/terms",
        "/api/users/nicknames/availability",
        // 정산 공유 링크는 공유 토큰 자체가 접근 수단이라 인증을 요구하지 않는다.
        "/api/settlement-shares",
        "/health",
        "/actuator/health",
        "/swagger-ui.html",
    )

    var publicPathPrefixes: List<String> = listOf(
        "/actuator",
        "/swagger-ui",
        "/v3/api-docs",
        "/api/local-test",
        "/uploads/post-attachments",
        "/uploads/user-profile-images",
    )
}
