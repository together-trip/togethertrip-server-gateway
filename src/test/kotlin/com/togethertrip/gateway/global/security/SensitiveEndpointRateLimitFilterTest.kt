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
        settlementShareRequestsPerMinute = 1
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
    fun `같은 IP의 정산 공유 조회가 한도를 넘으면 429를 반환한다`() {
        assertPass(shareLookup("192.0.2.50"))
        val limited = shareLookup("192.0.2.50")

        StepVerifier.create(filter.filter(limited) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.response.statusCode)
    }

    @Test
    fun `정산 공유 rate limit은 다른 remote address를 독립 key로 사용한다`() {
        assertPass(shareLookup("192.0.2.60"))
        assertPass(shareLookup("192.0.2.61"))
    }

    @Test
    fun `일반 GET API는 rate limit 대상이 아니다`() {
        repeat(3) {
            assertPass(
                MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/trips/1")
                        .remoteAddress(InetSocketAddress("192.0.2.70", 43100))
                        .build(),
                ),
            )
        }
    }

    @Test
    fun `일반 API는 rate limit 대상이 아니다`() {
        repeat(3) { assertPass(post("/api/trips", "192.0.2.30")) }
    }

    @Test
    fun `trusted proxy가 정제한 client IP를 로그인 key로 사용한다`() {
        val transformer = TrustedProxyForwardedHeaderConfiguration(
            GatewayTrustedProxyProperties().apply { cidrs = listOf("10.0.0.0/8") },
        ).forwardedHeaderTransformer()
        val firstClient = proxiedLogin(
            transformer,
            proxyIp = "10.0.0.10",
            clientIp = "203.0.113.66, 198.51.100.101",
        )
        val secondClient = proxiedLogin(transformer, proxyIp = "10.0.0.10", clientIp = "198.51.100.102")

        assertPass(firstClient)
        assertPass(secondClient)

        val limited = proxiedLogin(
            transformer,
            proxyIp = "10.0.0.10",
            clientIp = "203.0.113.77, 198.51.100.101",
        )
        StepVerifier.create(filter.filter(limited) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.response.statusCode)
    }

    @Test
    fun `신뢰하지 않은 요청의 XFF는 로그인 key를 바꾸지 못한다`() {
        val transformer = TrustedProxyForwardedHeaderConfiguration(
            GatewayTrustedProxyProperties().apply { cidrs = listOf("10.0.0.0/8") },
        ).forwardedHeaderTransformer()

        assertPass(proxiedLogin(transformer, proxyIp = "192.0.2.201", clientIp = "198.51.100.201"))
        val limited = proxiedLogin(transformer, proxyIp = "192.0.2.201", clientIp = "198.51.100.202")

        StepVerifier.create(filter.filter(limited) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.response.statusCode)
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

    private fun shareLookup(ip: String): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/settlement-shares?token=share-token")
            .remoteAddress(InetSocketAddress(ip, 43100))
            .build(),
    )

    private fun report(userId: String): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/trips/77/reports")
            .remoteAddress(InetSocketAddress("192.0.2.40", 43100))
            .header(GatewayAuthenticationFilter.USER_ID_HEADER, userId)
            .build(),
    )

    private fun proxiedLogin(
        transformer: org.springframework.web.server.adapter.ForwardedHeaderTransformer,
        proxyIp: String,
        clientIp: String,
    ): MockServerWebExchange {
        val request = MockServerHttpRequest.post("http://gateway/api/auth/oauth/apple")
            .remoteAddress(InetSocketAddress(proxyIp, 43100))
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-For", clientIp)
            .build()
        val transformed = transformer.apply(request)
        val mockRequest = MockServerHttpRequest.post(transformed.uri.toString())
            .headers(transformed.headers)
            .remoteAddress(transformed.remoteAddress!!)
            .build()
        return MockServerWebExchange.from(mockRequest)
    }
}
