package com.togethertrip.gateway.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
@ConfigurationProperties(prefix = "gateway.transport-security")
class GatewayTransportSecurityProperties {
    var requireHttps: Boolean = false
    var healthProbePaths: List<String> = listOf("/health", "/actuator/health")
}

@Component
class GatewayTransportSecurityFilter(
    private val properties: GatewayTransportSecurityProperties,
) : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!properties.requireHttps || path in properties.healthProbePaths) {
            return chain.filter(exchange)
        }

        if (exchange.request.sslInfo == null && exchange.request.uri.scheme != HTTPS_SCHEME) {
            return rejectInsecureRequest(exchange)
        }

        exchange.response.headers.set(HSTS_HEADER, HSTS_VALUE)
        return chain.filter(exchange)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 5

    private fun rejectInsecureRequest(exchange: ServerWebExchange): Mono<Void> {
        return GatewayErrorResponseWriter.write(
            response = exchange.response,
            status = HttpStatus.UPGRADE_REQUIRED,
            code = "HTTPS_REQUIRED",
            message = "보안 연결이 필요합니다.",
        )
    }

    companion object {
        private const val HTTPS_SCHEME = "https"
        private const val HSTS_HEADER = "Strict-Transport-Security"
        private const val HSTS_VALUE = "max-age=31536000; includeSubDomains"
    }
}
