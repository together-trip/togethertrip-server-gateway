package com.togethertrip.gateway.global.logging

import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GatewayRequestLoggingFilterTest {

    private val filter = GatewayRequestLoggingFilter(RequestIdGenerator())

    @Test
    fun `요청 ID가 없으면 생성하고 downstream 요청과 응답에 추가한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips").build(),
        )
        var downstreamRequestId: String? = null

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                downstreamRequestId = routedExchange.request.headers
                    .getFirst(GatewayRequestLoggingFilter.REQUEST_ID_HEADER)
                routedExchange.response.statusCode = HttpStatus.OK
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(
            downstreamRequestId,
            exchange.response.headers.getFirst(GatewayRequestLoggingFilter.REQUEST_ID_HEADER),
        )
    }

    @Test
    fun `기존 요청 ID를 유지하고 spoofing user header를 제거한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips")
                .header(GatewayRequestLoggingFilter.REQUEST_ID_HEADER, "request-123")
                .header(GatewayRequestLoggingFilter.SPOOFABLE_USER_ID_HEADER, "999")
                .build(),
        )

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                assertEquals(
                    "request-123",
                    routedExchange.request.headers.getFirst(GatewayRequestLoggingFilter.REQUEST_ID_HEADER),
                )
                assertNull(
                    routedExchange.request.headers.getFirst(GatewayRequestLoggingFilter.SPOOFABLE_USER_ID_HEADER),
                )
                routedExchange.response.statusCode = HttpStatus.OK
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(
            "request-123",
            exchange.response.headers.getFirst(GatewayRequestLoggingFilter.REQUEST_ID_HEADER),
        )
    }

    @Test
    fun `route 정보를 가진 요청도 정상 완료한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips?token=secret").build(),
        )
        exchange.attributes[ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR] = Route.async()
            .id("main")
            .uri(URI.create("http://main:8081"))
            .predicate { true }
            .build()

        StepVerifier.create(
            filter.filter(exchange, GatewayFilterChain { routedExchange ->
                routedExchange.response.statusCode = HttpStatus.OK
                Mono.empty()
            }),
        ).verifyComplete()
    }
}
