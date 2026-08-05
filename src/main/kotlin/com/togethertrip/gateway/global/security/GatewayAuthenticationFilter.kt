package com.togethertrip.gateway.global.security

import com.togethertrip.gateway.global.security.jwt.JwtTokenProvider
import com.togethertrip.gateway.global.security.jwt.TokenType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class GatewayAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val gatewaySecurityProperties: GatewaySecurityProperties,
    @Value("\${auth.local-test.enabled:false}")
    private val localTestEnabled: Boolean,
) : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(GatewayAuthenticationFilter::class.java)

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val sanitizedExchange = exchange.withoutSpoofableAuthHeaders()

        if (isPublicPath(sanitizedExchange)) {
            return chain.filter(sanitizedExchange)
        }

        val token = resolveToken(sanitizedExchange)
            ?: return unauthorized(sanitizedExchange, "missing bearer token")

        if (localTestEnabled && token.startsWith(LOCAL_TEST_TOKEN_PREFIX)) {
            if (isNotificationPath(sanitizedExchange)) {
                val identity = LocalTestIdentity.parse(token)
                    ?: return unauthorized(sanitizedExchange, "invalid notification local-test identity")
                return chain.filter(sanitizedExchange.withAuthenticatedIdentity(identity.userId, identity.role.name))
            }
            return chain.filter(sanitizedExchange)
        }

        if (!jwtTokenProvider.validateToken(token)) {
            return unauthorized(sanitizedExchange, "invalid bearer token")
        }

        val claims = runCatching { jwtTokenProvider.getClaims(token) }
            .getOrElse { return unauthorized(sanitizedExchange, "invalid token claims") }
        if (claims.tokenType != TokenType.ACCESS) {
            return unauthorized(sanitizedExchange, "unsupported token type")
        }

        return chain.filter(sanitizedExchange.withAuthenticatedIdentity(claims.userId, claims.role.name))
    }

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 10
    }

    private fun ServerWebExchange.withoutSpoofableAuthHeaders(): ServerWebExchange {
        val sanitizedRequest = request.mutate()
            .headers {
                it.remove(USER_ID_HEADER)
                it.remove(USER_ROLE_HEADER)
            }
            .build()

        return mutate().request(sanitizedRequest).build()
    }

    private fun isPublicPath(exchange: ServerWebExchange): Boolean {
        val path = exchange.request.path.pathWithinApplication().value()
        return path in gatewaySecurityProperties.publicPaths ||
            gatewaySecurityProperties.publicPathPrefixes.any(path::startsWith)
    }

    private fun isNotificationPath(exchange: ServerWebExchange): Boolean =
        exchange.request.path.pathWithinApplication().value().startsWith(NOTIFICATION_PATH_PREFIX)

    private fun ServerWebExchange.withAuthenticatedIdentity(userId: Long, role: String): ServerWebExchange {
        val authenticatedRequest = request.mutate()
            .header(USER_ID_HEADER, userId.toString())
            .header(USER_ROLE_HEADER, role)
            .build()
        return mutate().request(authenticatedRequest).build()
    }

    private fun resolveToken(exchange: ServerWebExchange): String? {
        val authorizationHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: return null

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null
        }

        return authorizationHeader.removePrefix(BEARER_PREFIX).takeIf { it.isNotBlank() }
    }

    private fun unauthorized(
        exchange: ServerWebExchange,
        reason: String,
    ): Mono<Void> {
        log.debug(
            "gateway authentication rejected method={} path={} reason={}",
            exchange.request.method,
            exchange.request.path.pathWithinApplication().value(),
            reason,
        )
        return GatewayErrorResponseWriter.write(
            response = exchange.response,
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            message = "인증이 필요합니다.",
        )
    }

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
        const val USER_ROLE_HEADER = "X-User-Role"
        private const val BEARER_PREFIX = "Bearer "
        private const val LOCAL_TEST_TOKEN_PREFIX = "local-test:"
        private const val NOTIFICATION_PATH_PREFIX = "/notification/"
    }
}
