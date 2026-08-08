package com.togethertrip.gateway.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
class GatewayRateLimitProperties {
    var enabled: Boolean = true
    var loginRequestsPerMinute: Int = 20
    var reportRequestsPerMinute: Int = 5
    var settlementShareRequestsPerMinute: Int = 60
    var maxTrackedKeys: Int = 20_000
}

@Component
class SensitiveEndpointRateLimitFilter(
    private val properties: GatewayRateLimitProperties,
) : GlobalFilter, Ordered {

    private val clock = Clock.systemUTC()
    private val windows = ConcurrentHashMap<String, RequestWindow>()
    private val requestSequence = AtomicLong()

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        if (!properties.enabled) {
            return chain.filter(exchange)
        }

        val policy = resolvePolicy(exchange) ?: return chain.filter(exchange)
        if (!tryAcquire(policy.key, policy.limit)) {
            return reject(exchange)
        }
        return chain.filter(exchange)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 20

    private fun resolvePolicy(exchange: ServerWebExchange): RateLimitPolicy? {
        val path = exchange.request.path.pathWithinApplication().value()

        // 인증 없이 열리는 경로라 유출된 토큰을 대량으로 조회하는 트래픽을 제한한다.
        if (exchange.request.method == HttpMethod.GET && path == SETTLEMENT_SHARE_PATH) {
            val address = exchange.request.remoteAddress?.hostString ?: UNKNOWN_CLIENT
            return RateLimitPolicy(
                "settlement-share:$address",
                properties.settlementShareRequestsPerMinute,
            )
        }

        if (exchange.request.method != HttpMethod.POST) {
            return null
        }
        if (path in LOGIN_PATHS) {
            val address = exchange.request.remoteAddress?.hostString ?: UNKNOWN_CLIENT
            return RateLimitPolicy("login:$address", properties.loginRequestsPerMinute)
        }
        if (REPORT_PATH.matches(path)) {
            val userId = exchange.request.headers.getFirst(GatewayAuthenticationFilter.USER_ID_HEADER)
                ?: return RateLimitPolicy("report:$UNKNOWN_CLIENT", properties.reportRequestsPerMinute)
            return RateLimitPolicy("report:$userId", properties.reportRequestsPerMinute)
        }
        return null
    }

    private fun tryAcquire(key: String, limit: Int): Boolean {
        if (limit <= 0) {
            return false
        }
        val now = clock.millis()
        if (requestSequence.incrementAndGet() % CLEANUP_INTERVAL == 0L) {
            windows.entries.removeIf { now - it.value.startedAtMillis >= WINDOW_MILLIS }
        }
        if (!windows.containsKey(key) && windows.size >= properties.maxTrackedKeys) {
            return false
        }
        val current = windows.compute(key) { _, previous ->
            if (previous == null || now - previous.startedAtMillis >= WINDOW_MILLIS) {
                RequestWindow(startedAtMillis = now, count = 1)
            } else {
                previous.copy(count = previous.count + 1)
            }
        } ?: return false
        return current.count <= limit
    }

    private fun reject(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.headers.set("Retry-After", WINDOW_SECONDS.toString())
        return GatewayErrorResponseWriter.write(
            response = response,
            status = HttpStatus.TOO_MANY_REQUESTS,
            code = "RATE_LIMIT_EXCEEDED",
            message = "요청이 너무 많습니다.",
        )
    }

    private data class RequestWindow(val startedAtMillis: Long, val count: Int)
    private data class RateLimitPolicy(val key: String, val limit: Int)

    companion object {
        private val LOGIN_PATHS = setOf(
            "/api/auth/oauth/kakao",
            "/api/auth/oauth/apple",
            "/api/auth/refresh",
        )
        private val REPORT_PATH = Regex("^/api/trips/[^/]+/reports$")
        private const val SETTLEMENT_SHARE_PATH = "/api/settlement-shares"
        private const val UNKNOWN_CLIENT = "unknown"
        private const val WINDOW_SECONDS = 60L
        private const val WINDOW_MILLIS = WINDOW_SECONDS * 1000
        private const val CLEANUP_INTERVAL = 256L
    }
}
