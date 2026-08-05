package com.togethertrip.gateway.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.adapter.ForwardedHeaderTransformer
import java.net.InetAddress

@Component
@ConfigurationProperties(prefix = "gateway.trusted-proxy")
class GatewayTrustedProxyProperties {
    var cidrs: List<String> = listOf("127.0.0.1/32", "::1/128")
}

@Configuration
class TrustedProxyForwardedHeaderConfiguration(
    private val properties: GatewayTrustedProxyProperties,
) {

    @Bean("forwardedHeaderTransformer")
    fun forwardedHeaderTransformer(): ForwardedHeaderTransformer {
        check(properties.cidrs.isNotEmpty()) { "gateway.trusted-proxy.cidrs must not be empty" }
        val trustedNetworks = properties.cidrs.map(IpNetwork::parse)
        val trustedTransformer = ForwardedHeaderTransformer()
        val removeOnlyTransformer = ForwardedHeaderTransformer().apply { setRemoveOnly(true) }

        return object : ForwardedHeaderTransformer() {
            override fun apply(request: ServerHttpRequest): ServerHttpRequest {
                val remoteAddress = request.remoteAddress?.address
                return if (remoteAddress != null && trustedNetworks.any { it.contains(remoteAddress) }) {
                    trustedTransformer.apply(request)
                } else {
                    removeOnlyTransformer.apply(request)
                }
            }
        }
    }

    private data class IpNetwork(
        private val networkBytes: ByteArray,
        private val prefixLength: Int,
    ) {
        fun contains(address: InetAddress): Boolean {
            val candidate = address.address
            if (candidate.size != networkBytes.size) {
                return false
            }
            val fullBytes = prefixLength / 8
            val remainderBits = prefixLength % 8
            for (index in 0 until fullBytes) {
                if (candidate[index] != networkBytes[index]) {
                    return false
                }
            }
            if (remainderBits == 0) {
                return true
            }
            val mask = (0xFF shl (8 - remainderBits)) and 0xFF
            return (candidate[fullBytes].toInt() and mask) == (networkBytes[fullBytes].toInt() and mask)
        }

        companion object {
            fun parse(value: String): IpNetwork {
                val parts = value.trim().split('/', limit = 2)
                check(parts.size == 2) { "trusted proxy CIDR must include prefix length: $value" }
                val address = InetAddress.getByName(parts[0])
                val prefixLength = parts[1].toIntOrNull()
                    ?: error("trusted proxy CIDR prefix is invalid: $value")
                check(prefixLength in 1..(address.address.size * 8)) {
                    "trusted proxy CIDR prefix is out of range: $value"
                }
                return IpNetwork(address.address, prefixLength)
            }
        }
    }
}
