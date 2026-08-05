package com.togethertrip.gateway.global.security

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GatewayCorsConfigurationTest {

    @Test
    fun `allowlist origin의 preflight만 허용한다`() {
        val filter = GatewayCorsConfiguration(
            GatewayCorsProperties().apply {
                allowedOrigins = listOf("https://app.togethertrip.co.kr")
            },
        ).corsWebFilter()
        val exchange = preflight("https://app.togethertrip.co.kr")

        StepVerifier.create(filter.filter(exchange) { Mono.error(AssertionError("preflight should end at cors filter")) })
            .verifyComplete()

        assertEquals(HttpStatus.OK, exchange.response.statusCode ?: HttpStatus.OK)
        assertEquals(
            "https://app.togethertrip.co.kr",
            exchange.response.headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
        )
    }

    @Test
    fun `allowlist 밖 origin의 preflight는 거부한다`() {
        val filter = GatewayCorsConfiguration(
            GatewayCorsProperties().apply {
                allowedOrigins = listOf("https://app.togethertrip.co.kr")
            },
        ).corsWebFilter()
        val exchange = preflight("https://evil.example")

        StepVerifier.create(filter.filter(exchange) { Mono.error(AssertionError("downstream should not be called")) })
            .verifyComplete()

        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `credentials를 허용할 때 wildcard origin 설정은 실패한다`() {
        val properties = GatewayCorsProperties().apply { allowedOrigins = listOf("*") }

        assertFailsWith<IllegalStateException> {
            GatewayCorsConfiguration(properties).corsWebFilter()
        }
    }

    private fun preflight(origin: String): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.options("https://api.togethertrip.co.kr/api/auth/oauth/apple")
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            .build(),
    )
}
