package com.togethertrip.gateway.global.security

import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

object GatewayErrorResponseWriter {
    fun write(
        response: ServerHttpResponse,
        status: HttpStatusCode,
        code: String,
        message: String,
    ): Mono<Void> {
        response.statusCode = status
        response.headers.remove("Content-Length")
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = """{"success":false,"code":"$code","message":"$message"}"""
            .toByteArray(StandardCharsets.UTF_8)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
    }
}
