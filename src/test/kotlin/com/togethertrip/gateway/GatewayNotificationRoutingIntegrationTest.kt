package com.togethertrip.gateway

import com.togethertrip.gateway.global.security.GatewayAuthenticationFilter
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.netty.handler.codec.http.HttpResponseStatus
import org.junit.jupiter.api.AfterAll
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayNotificationRoutingIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `notification 경로는 인증 후 notification 서비스로 라우팅하고 사용자 헤더를 전달한다`() {
        receivedRequest.set(null)
        receivedCount.set(0)

        webTestClient().get()
            .uri("/notification/messages")
            .header("Authorization", "Bearer ${createAccessToken(userId = 123)}")
            .header(GatewayAuthenticationFilter.USER_ID_HEADER, "999")
            .header(GatewayAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("notification-ok")

        val request = receivedRequest.get()
            ?: throw AssertionError("notification downstream should receive request")

        assertEquals(1, receivedCount.get())
        assertEquals("/notification/messages", request.uri)
        assertEquals("123", request.userId)
        assertEquals("USER", request.userRole)
    }

    @Test
    fun `notification 경로는 토큰이 없으면 downstream으로 라우팅하지 않는다`() {
        receivedRequest.set(null)
        receivedCount.set(0)

        webTestClient().get()
            .uri("/notification/messages")
            .exchange()
            .expectStatus().isUnauthorized

        assertEquals(0, receivedCount.get())
    }

    private fun createAccessToken(userId: Long): String {
        val now = Instant.now()

        return Jwts.builder()
            .issuer(JWT_ISSUER)
            .subject(userId.toString())
            .claim("role", "USER")
            .claim("tokenType", "ACCESS")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.toByteArray()))
            .compact()
    }

    private fun webTestClient(): WebTestClient {
        return WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:$port")
            .build()
    }

    private data class ReceivedRequest(
        val uri: String,
        val userId: String?,
        val userRole: String?,
    )

    @TestConfiguration
    class NotificationRouteTestConfig {

        @Bean
        fun notificationRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
            return builder.routes()
                .route("notification") { route ->
                    route.path("/notification/**")
                        .uri(notificationUri())
                }
                .build()
        }
    }

    companion object {
        private const val JWT_SECRET = "test-development-secret-key-at-least-32-bytes"
        private const val JWT_ISSUER = "together-trip"

        private val receivedRequest = AtomicReference<ReceivedRequest?>()
        private val receivedCount = AtomicInteger(0)
        private val notificationServer: DisposableServer = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle { request, response ->
                receivedCount.incrementAndGet()
                receivedRequest.set(
                    ReceivedRequest(
                        uri = request.uri(),
                        userId = request.requestHeaders().get(GatewayAuthenticationFilter.USER_ID_HEADER),
                        userRole = request.requestHeaders().get(GatewayAuthenticationFilter.USER_ROLE_HEADER),
                    ),
                )
                response.status(HttpResponseStatus.OK)
                    .sendString(Mono.just("notification-ok"))
            }
            .bindNow()

        fun notificationUri(): String {
            return "http://127.0.0.1:${notificationServer.port()}"
        }

        @JvmStatic
        @AfterAll
        fun stopNotificationServer() {
            notificationServer.disposeNow()
        }
    }
}
