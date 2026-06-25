package com.togethertrip.gateway.global.security

import com.togethertrip.gateway.global.security.jwt.JwtProperties
import com.togethertrip.gateway.global.security.jwt.JwtTokenProvider
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GatewayAuthenticationFilterTest {

    private val jwtProperties = JwtProperties(
        secret = "test-development-secret-key-at-least-32-bytes",
        issuer = "together-trip",
        accessTokenExpiration = 1800,
        refreshTokenExpiration = 1209600,
    )
    private val filter = GatewayAuthenticationFilter(
        jwtTokenProvider = JwtTokenProvider(jwtProperties),
        localTestEnabled = false,
    )

    @Test
    fun `공개 경로는 토큰 없이 통과한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/phone/request").build(),
        )
        var called = false

        StepVerifier.create(
            filter.filter(exchange) {
                called = true
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(true, called)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/uploads/post-attachments/trip-photo.jpg",
            "/uploads/user-profile-images/profile.png",
        ],
    )
    fun `업로드 정적 파일 경로는 토큰 없이 통과한다`(path: String) {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(path).build(),
        )
        var called = false

        StepVerifier.create(
            filter.filter(exchange) {
                called = true
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(true, called)
    }

    @Test
    fun `보호 경로는 토큰이 없으면 401로 차단한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips").build(),
        )

        StepVerifier.create(
            filter.filter(exchange) {
                Mono.error(AssertionError("downstream should not be called"))
            },
        ).verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `access token이 유효하면 사용자 헤더를 downstream에 전달한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${createToken(tokenType = "ACCESS")}")
                .header(GatewayAuthenticationFilter.USER_ID_HEADER, "999")
                .build(),
        )

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                assertEquals(
                    "123",
                    routedExchange.request.headers.getFirst(GatewayAuthenticationFilter.USER_ID_HEADER),
                )
                assertEquals(
                    "USER",
                    routedExchange.request.headers.getFirst(GatewayAuthenticationFilter.USER_ROLE_HEADER),
                )
                Mono.empty()
            },
        ).verifyComplete()
    }

    @Test
    fun `refresh token은 보호 경로 인증에 사용할 수 없다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${createToken(tokenType = "REFRESH")}")
                .build(),
        )

        StepVerifier.create(
            filter.filter(exchange) {
                Mono.error(AssertionError("downstream should not be called"))
            },
        ).verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `claim이 부족한 토큰은 401로 차단한다`() {
        val now = Instant.now()
        val token = Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject("123")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()))
            .compact()
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .build(),
        )

        StepVerifier.create(
            filter.filter(exchange) {
                Mono.error(AssertionError("downstream should not be called"))
            },
        ).verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `local-test 토큰은 local 테스트 인증이 켜져 있으면 downstream으로 넘긴다`() {
        val localTestFilter = GatewayAuthenticationFilter(
            jwtTokenProvider = JwtTokenProvider(jwtProperties),
            localTestEnabled = true,
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer local-test:verified:swagger")
                .build(),
        )
        var called = false

        StepVerifier.create(
            localTestFilter.filter(exchange) {
                called = true
                Mono.empty()
            },
        ).verifyComplete()

        assertEquals(true, called)
    }

    @Test
    fun `공개 경로에서도 spoofing 사용자 헤더는 제거한다`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/refresh")
                .header(GatewayAuthenticationFilter.USER_ID_HEADER, "999")
                .header(GatewayAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                .build(),
        )

        StepVerifier.create(
            filter.filter(exchange) { routedExchange ->
                assertNull(routedExchange.request.headers.getFirst(GatewayAuthenticationFilter.USER_ID_HEADER))
                assertNull(routedExchange.request.headers.getFirst(GatewayAuthenticationFilter.USER_ROLE_HEADER))
                Mono.empty()
            },
        ).verifyComplete()
    }

    private fun createToken(tokenType: String): String {
        val now = Instant.now()

        return Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject("123")
            .claim("role", "USER")
            .claim("tokenType", tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()))
            .compact()
    }
}
