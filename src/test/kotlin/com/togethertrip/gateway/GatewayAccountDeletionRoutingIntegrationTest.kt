package com.togethertrip.gateway

import com.togethertrip.gateway.global.security.GatewayAuthenticationFilter
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.netty.handler.codec.http.HttpResponseStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.time.Instant
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAccountDeletionRoutingIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun resetRequests() {
        receivedRequests.clear()
    }

    @Test
    fun `계정 삭제 요청은 access token claim 헤더로 main에 전달한다`() {
        val token = createToken(userId = 123L, tokenType = "ACCESS")

        webTestClient().delete()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .header(GatewayAuthenticationFilter.USER_ID_HEADER, "999")
            .header(GatewayAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
            .exchange()
            .expectStatus().isNoContent

        val request = receivedRequests.single()
        assertEquals("DELETE", request.method)
        assertEquals("/api/users/me", request.uri)
        assertEquals("Bearer $token", request.authorization)
        assertEquals("123", request.userId)
        assertEquals("USER", request.userRole)
    }

    @Test
    fun `push token 삭제 요청은 인증 후 notification에 전달한다`() {
        val token = createToken(userId = 77L, tokenType = "ACCESS")

        webTestClient().delete()
            .uri("/notification/api/push-tokens")
            .header("Authorization", "Bearer $token")
            .header(GatewayAuthenticationFilter.USER_ID_HEADER, "999")
            .exchange()
            .expectStatus().isNoContent

        val request = receivedRequests.single()
        assertEquals("DELETE", request.method)
        assertEquals("/notification/api/push-tokens", request.uri)
        assertEquals("77", request.userId)
        assertEquals("USER", request.userRole)
    }

    @Test
    fun `삭제 경로는 토큰이 없거나 refresh token이면 downstream에 도달하지 않는다`() {
        webTestClient().delete()
            .uri("/api/users/me")
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient().delete()
            .uri("/notification/api/push-tokens")
            .header("Authorization", "Bearer ${createToken(7L, "REFRESH")}")
            .exchange()
            .expectStatus().isUnauthorized

        assertEquals(0, receivedRequests.size)
    }

    @Test
    fun `만료되거나 다른 키로 서명한 access token은 삭제 경로에서 차단한다`() {
        webTestClient().delete()
            .uri("/api/users/me")
            .header("Authorization", "Bearer ${createToken(7L, "ACCESS", expired = true)}")
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient().delete()
            .uri("/api/users/me")
            .header("Authorization", "Bearer ${createToken(7L, "ACCESS", forged = true)}")
            .exchange()
            .expectStatus().isUnauthorized

        assertEquals(0, receivedRequests.size)
    }

    private fun createToken(
        userId: Long,
        tokenType: String,
        expired: Boolean = false,
        forged: Boolean = false,
    ): String {
        val now = Instant.now()
        val issuedAt = if (expired) now.minusSeconds(120) else now
        val expiresAt = if (expired) now.minusSeconds(60) else now.plusSeconds(60)
        val secret = if (forged) FORGED_JWT_SECRET else JWT_SECRET

        return Jwts.builder()
            .issuer(JWT_ISSUER)
            .subject(userId.toString())
            .claim("role", "USER")
            .claim("tokenType", tokenType)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()))
            .compact()
    }

    private fun webTestClient(): WebTestClient =
        WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:$port")
            .build()

    private data class ReceivedRequest(
        val method: String,
        val uri: String,
        val authorization: String?,
        val userId: String?,
        val userRole: String?,
    )

    @TestConfiguration
    class AccountDeletionRouteTestConfig {
        @Bean
        fun accountDeletionRouteLocator(builder: RouteLocatorBuilder): RouteLocator =
            builder.routes()
                .route("account-deletion-main") { route ->
                    route.path("/api/**").uri(downstreamUri())
                }
                .route("account-deletion-notification") { route ->
                    route.path("/notification/**").uri(downstreamUri())
                }
                .build()
    }

    companion object {
        private const val JWT_SECRET = "test-development-secret-key-at-least-32-bytes"
        private const val FORGED_JWT_SECRET = "forged-development-secret-key-at-least-32-bytes"
        private const val JWT_ISSUER = "together-trip"

        private val receivedRequests = CopyOnWriteArrayList<ReceivedRequest>()
        private val downstreamServer: DisposableServer = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle { request, response ->
                receivedRequests += ReceivedRequest(
                    method = request.method().name(),
                    uri = request.uri(),
                    authorization = request.requestHeaders().get("Authorization"),
                    userId = request.requestHeaders().get(GatewayAuthenticationFilter.USER_ID_HEADER),
                    userRole = request.requestHeaders().get(GatewayAuthenticationFilter.USER_ROLE_HEADER),
                )
                response.status(HttpResponseStatus.NO_CONTENT).send(Mono.empty())
            }
            .bindNow()

        fun downstreamUri(): String = "http://127.0.0.1:${downstreamServer.port()}"

        @JvmStatic
        @AfterAll
        fun stopDownstreamServer() {
            downstreamServer.disposeNow()
        }
    }
}
