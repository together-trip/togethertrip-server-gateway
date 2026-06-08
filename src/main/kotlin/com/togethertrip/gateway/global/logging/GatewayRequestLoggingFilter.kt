package com.togethertrip.gateway.global.logging

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

@Component
class GatewayRequestLoggingFilter(
    private val requestIdGenerator: RequestIdGenerator,
) : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(GatewayRequestLoggingFilter::class.java)

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val startedAt = System.nanoTime()
        val requestId = resolveRequestId(exchange)
        val failure = AtomicReference<Throwable?>()
        val request = exchange.request.mutate()
            .headers {
                it.remove(SPOOFABLE_USER_ID_HEADER)
                it.set(REQUEST_ID_HEADER, requestId)
            }
            .build()
        val routedExchange = exchange.mutate().request(request).build()

        routedExchange.response.headers.set(REQUEST_ID_HEADER, requestId)

        return chain.filter(routedExchange)
            .doOnError { failure.set(it) }
            .doFinally {
                logRequest(routedExchange, elapsedMillis(startedAt), failure.get())
            }
    }

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }

    private fun resolveRequestId(exchange: ServerWebExchange): String {
        val requestId = exchange.request.headers.getFirst(REQUEST_ID_HEADER)
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_REQUEST_ID_LENGTH)
            ?.takeIf { REQUEST_ID_PATTERN.matches(it) }

        return requestId ?: requestIdGenerator.generate()
    }

    private fun logRequest(
        exchange: ServerWebExchange,
        elapsedMs: Long,
        failure: Throwable?,
    ) {
        val request = exchange.request
        val routeId = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)?.id
            ?: "unknown"
        val path = buildPath(exchange)
        val status = exchange.response.statusCode?.value() ?: 0

        if (failure == null) {
            log.info(
                "gateway request completed routeId={} method={} path={} status={} elapsedMs={}",
                routeId,
                request.method,
                path,
                status,
                elapsedMs,
            )
        } else {
            log.error(
                "gateway request failed routeId={} method={} path={} status={} elapsedMs={} exception={}",
                routeId,
                request.method,
                path,
                status,
                elapsedMs,
                failure::class.simpleName,
                failure,
            )
        }
    }

    private fun buildPath(exchange: ServerWebExchange): String {
        val uri = exchange.request.uri
        val query = uri.rawQuery
            ?.let(SensitiveDataMasker::mask)
            ?.let { "?$it" }
            ?: ""

        return "${uri.rawPath}$query"
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return (System.nanoTime() - startedAt) / 1_000_000
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val SPOOFABLE_USER_ID_HEADER = "X-User-Id"
        private const val MAX_REQUEST_ID_LENGTH = 100
        private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9._:-]+")
    }
}
