package com.togethertrip.gateway.global.security

import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrustedProxyForwardedHeaderConfigurationTest {

    private val transformer = TrustedProxyForwardedHeaderConfiguration(
        GatewayTrustedProxyProperties().apply {
            cidrs = listOf("10.0.0.0/8", "2001:db8::/32")
        },
    ).forwardedHeaderTransformer()

    @Test
    fun `신뢰하지 않은 원격 주소의 forwarded header는 제거하고 사용하지 않는다`() {
        val request = MockServerHttpRequest.get("http://gateway/api/trips")
            .remoteAddress(InetSocketAddress("192.0.2.10", 43100))
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-For", "198.51.100.7")
            .build()

        val transformed = transformer.apply(request)

        assertEquals("http", transformed.uri.scheme)
        assertEquals("192.0.2.10", transformed.remoteAddress?.address?.hostAddress)
        assertNull(transformed.headers.getFirst("X-Forwarded-Proto"))
        assertNull(transformed.headers.getFirst("X-Forwarded-For"))
    }

    @Test
    fun `신뢰 proxy의 forwarded header만 요청 정보에 반영한다`() {
        val request = MockServerHttpRequest.get("http://gateway/api/trips")
            .remoteAddress(InetSocketAddress("10.20.30.40", 43100))
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "api.togethertrip.co.kr")
            .header("X-Forwarded-For", "198.51.100.7")
            .build()

        val transformed = transformer.apply(request)

        assertEquals("https", transformed.uri.scheme)
        assertEquals("api.togethertrip.co.kr", transformed.uri.host)
        assertEquals("198.51.100.7", transformed.remoteAddress?.hostString)
        assertNull(transformed.headers.getFirst("X-Forwarded-Proto"))
    }

    @Test
    fun `신뢰 proxy가 append한 XFF의 오른쪽 client IP를 사용해 prefix 위조를 무시한다`() {
        val request = MockServerHttpRequest.get("http://gateway/api/trips")
            .remoteAddress(InetSocketAddress("10.20.30.40", 43100))
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-For", "203.0.113.99, 198.51.100.17")
            .build()

        val transformed = transformer.apply(request)

        assertEquals("198.51.100.17", transformed.remoteAddress?.hostString)
        assertNull(transformed.headers.getFirst("X-Forwarded-For"))
    }

    @Test
    fun `IPv6 trusted proxy CIDR도 지원한다`() {
        val request = MockServerHttpRequest.get("http://gateway/api/trips")
            .remoteAddress(InetSocketAddress("2001:db8::10", 43100))
            .header("Forwarded", "for=198.51.100.8;proto=https;host=api.togethertrip.co.kr")
            .build()

        val transformed = transformer.apply(request)

        assertEquals("https", transformed.uri.scheme)
        assertEquals("198.51.100.8", transformed.remoteAddress?.hostString)
    }

    @Test
    fun `모든 주소를 신뢰하는 CIDR은 시작 시 거부한다`() {
        val configuration = TrustedProxyForwardedHeaderConfiguration(
            GatewayTrustedProxyProperties().apply { cidrs = listOf("0.0.0.0/0") },
        )

        assertFailsWith<IllegalStateException> {
            configuration.forwardedHeaderTransformer()
        }
    }
}
