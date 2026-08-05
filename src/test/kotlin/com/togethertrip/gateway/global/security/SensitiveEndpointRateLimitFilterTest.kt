package com.togethertrip.gateway.global.security

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.InetSocketAddress
import kotlin.test.assertEquals

class SensitiveEndpointRateLimitFilterTest {

    private val properties = GatewayRateLimitProperties().apply {
        enabled = true
        loginRequestsPerMinute = 1
        reportRequestsPerMinute = 1
    }
    private val filter = SensitiveEndpointRateLimitFilter(properties)

    @Test
    fun `같은 IP의 로그인 요청이 한도를 넘으면 429를 반환한다`() {
        assertPass(post("/api/auth/oauth/apple", "192.0.2.10"))
        val limited = post("/api/auth/oauth/apple", "192.0.2.10")

        StepVerifier.create(filter.filter(limited) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.response.statusCode)
    }

    @Test
    fun `로그인 rate limit은 다른 remote address를 독립 key로 사용한다`() {
        assertPass(post("/api/auth/oauth/kakao", "192.0.2.20"))
        assertPass(post("/api/auth/oauth/kakao", "192.0.2.21"))
    }

    @Test
    fun `신고 rate limit은 인증 사용자별로 적용한다`() {
        assertPass(report(userId = "123"))
        val limited = report(userId = "123")

        StepVerifier.create(filter.filter(limited) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.response.statusCode)
        assertPass(report(userId = "124"))
    }

    @Test
    fun `일반 API는 rate limit 대상이 아니다`() {
        repeat(3) { assertPass(post("/api/trips", "192.0.2.30")) }
    }

    private fun assertPass(exchange: MockServerWebExchange) {
        var called = false
        StepVerifier.create(
            filter.filter(exchange) {
                called = true
                Mono.empty()
            },
        ).verifyComplete()
        assertEquals(true, called)
    }

    private fun post(path: String, ip: String): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.post(path)
            .remoteAddress(InetSocketAddress(ip, 43100))
            .build(),
    )

    private fun report(userId: String): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/trips/77/reports")
            .remoteAddress(InetSocketAddress("192.0.2.40", 43100))
            .header(GatewayAuthenticationFilter.USER_ID_HEADER, userId)
            .build(),
    )
}
