package com.togethertrip.gateway.global.security

import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DownstreamErrorSanitizingFilterTest {

    private val filter = DownstreamErrorSanitizingFilter()

    @Test
    fun `downstream 5xx 본문은 공통 안전 응답으로 교체한다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/trips").build())

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                routedExchange.response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
                routedExchange.response.headers.add("X-Internal-Exception", "sql-secret")
                routedExchange.response.headers.add("Set-Cookie", "internal=session")
                routedExchange.response.writeWith(
                    Mono.just(
                        DefaultDataBufferFactory.sharedInstance.wrap(
                            "database password=secret stacktrace".toByteArray(),
                        ),
                    ),
                )
            },
        ).verifyComplete()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.response.statusCode)
        assertFalse(body.contains("secret"))
        assertFalse(body.contains("stacktrace"))
        assertEquals(null, exchange.response.headers.getFirst("X-Internal-Exception"))
        assertEquals(null, exchange.response.headers.getFirst("Set-Cookie"))
        assertEquals(true, body.contains("\"success\":false"))
        assertEquals(true, body.contains("DOWNSTREAM_SERVICE_ERROR"))
    }

    @Test
    fun `downstream 연결 예외 상세는 502 응답에 노출하지 않는다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/trips").build())

        StepVerifier.create(
            filter.filter(exchange) {
                Mono.error(IllegalStateException("jdbc://internal-host password=secret"))
            },
        ).verifyComplete()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.response.statusCode)
        assertFalse(body.contains("internal-host"))
        assertFalse(body.contains("secret"))
        assertEquals(true, body.contains("\"success\":false"))
        assertEquals(true, body.contains("DOWNSTREAM_SERVICE_UNAVAILABLE"))
    }

    @Test
    fun `본문 없는 downstream 5xx도 공통 안전 응답을 생성한다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/trips").build())

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                routedExchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
                routedExchange.response.setComplete()
            },
        ).verifyComplete()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
        assertEquals(true, body.contains("DOWNSTREAM_SERVICE_ERROR"))
    }

    @Test
    fun `downstream 4xx 본문은 도메인 계약대로 유지한다`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/trips").build())

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                routedExchange.response.statusCode = HttpStatus.CONFLICT
                routedExchange.response.writeWith(
                    Mono.just(
                        DefaultDataBufferFactory.sharedInstance.wrap(
                            "{\"code\":\"DUPLICATE_REPORT\"}".toByteArray(StandardCharsets.UTF_8),
                        ),
                    ),
                )
            },
        ).verifyComplete()

        assertEquals("{\"code\":\"DUPLICATE_REPORT\"}", exchange.response.bodyAsString.block())
    }
}
