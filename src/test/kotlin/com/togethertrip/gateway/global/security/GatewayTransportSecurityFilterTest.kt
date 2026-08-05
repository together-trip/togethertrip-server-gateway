package com.togethertrip.gateway.global.security

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals

class GatewayTransportSecurityFilterTest {

    private val filter = GatewayTransportSecurityFilter(
        GatewayTransportSecurityProperties().apply { requireHttps = true },
    )

    @Test
    fun `운영 HTTPS 강제 시 HTTP 요청은 거부한다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway/api/trips").build())

        StepVerifier.create(filter.filter(exchange) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()

        assertEquals(HttpStatus.UPGRADE_REQUIRED, exchange.response.statusCode)
    }

    @Test
    fun `클라이언트가 보낸 forwarded proto만으로 HTTP 요청을 신뢰하지 않는다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("http://gateway/api/trips")
                .header("X-Forwarded-Proto", "https")
                .build(),
        )

        StepVerifier.create(filter.filter(exchange) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()

        assertEquals(HttpStatus.UPGRADE_REQUIRED, exchange.response.statusCode)
    }

    @Test
    fun `정규화된 HTTPS 요청은 통과하고 HSTS를 응답한다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://gateway/api/trips").build())
        var called = false

        StepVerifier.create(
            filter.filter(exchange) {
                called = true
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(true, called)
        assertEquals(
            "max-age=31536000; includeSubDomains",
            exchange.response.headers.getFirst("Strict-Transport-Security"),
        )
    }

    @Test
    fun `health probe는 내부 HTTP 점검을 허용한다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway/actuator/health").build())
        var called = false

        StepVerifier.create(
            filter.filter(exchange) {
                called = true
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(true, called)
    }
}
