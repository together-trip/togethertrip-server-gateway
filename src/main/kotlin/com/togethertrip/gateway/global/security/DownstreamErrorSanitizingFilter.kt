package com.togethertrip.gateway.global.security

import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class DownstreamErrorSanitizingFilter : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(DownstreamErrorSanitizingFilter::class.java)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val decoratedResponse = object : ServerHttpResponseDecorator(exchange.response) {
            override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
                if (!statusCode.isServerError()) {
                    return super.writeWith(body)
                }
                return Flux.from(body)
                    .doOnNext(org.springframework.core.io.buffer.DataBufferUtils::release)
                    .then(
                        writeSafeError(
                            delegate,
                            statusCode ?: HttpStatus.INTERNAL_SERVER_ERROR,
                            code = "DOWNSTREAM_SERVICE_ERROR",
                            message = "서비스 처리 중 오류가 발생했습니다.",
                        ),
                    )
            }

            override fun writeAndFlushWith(body: Publisher<out Publisher<out DataBuffer>>): Mono<Void> {
                return writeWith(Flux.from(body).concatMap { it })
            }

            override fun setComplete(): Mono<Void> {
                if (!statusCode.isServerError()) {
                    return super.setComplete()
                }
                return writeSafeError(
                    delegate,
                    statusCode ?: HttpStatus.INTERNAL_SERVER_ERROR,
                    code = "DOWNSTREAM_SERVICE_ERROR",
                    message = "서비스 처리 중 오류가 발생했습니다.",
                )
            }
        }
        val routedExchange = exchange.mutate().response(decoratedResponse).build()

        return chain.filter(routedExchange).onErrorResume { failure ->
            log.warn("downstream request failed exception={}", failure::class.simpleName)
            if (exchange.response.isCommitted) {
                Mono.error(failure)
            } else {
                writeSafeError(
                    exchange.response,
                    HttpStatus.BAD_GATEWAY,
                    code = "DOWNSTREAM_SERVICE_UNAVAILABLE",
                    message = "서비스에 일시적으로 연결할 수 없습니다.",
                )
            }
        }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 30

    private fun writeSafeError(
        response: ServerHttpResponse,
        status: HttpStatusCode,
        code: String,
        message: String,
    ): Mono<Void> {
        response.headers.headerNames().removeIf { header ->
            header.equals("Server", ignoreCase = true) ||
                header.equals("Set-Cookie", ignoreCase = true) ||
                header.equals("WWW-Authenticate", ignoreCase = true) ||
                (header.startsWith("X-", ignoreCase = true) && !header.equals("X-Request-Id", ignoreCase = true))
        }
        return GatewayErrorResponseWriter.write(response, status, code, message)
    }

    private fun org.springframework.http.HttpStatusCode?.isServerError(): Boolean = this?.is5xxServerError == true

}
