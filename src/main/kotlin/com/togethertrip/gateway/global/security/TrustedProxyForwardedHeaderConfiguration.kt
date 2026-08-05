package com.togethertrip.gateway.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.adapter.ForwardedHeaderTransformer
import java.net.InetAddress
import java.net.InetSocketAddress

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
                    val clientAddress = resolveClientAddress(request, trustedNetworks)
                    trustedTransformer.apply(request).mutate()
                        .remoteAddress(clientAddress)
                        .build()
                } else {
                    removeOnlyTransformer.apply(request)
                }
            }
        }
    }

    private fun resolveClientAddress(
        request: ServerHttpRequest,
        trustedNetworks: List<IpNetwork>,
    ): InetSocketAddress {
        val directAddress = requireNotNull(request.remoteAddress)
        val forwardedAddresses = forwardedForValues(request)
            .mapNotNull(::parseAddressLiteral)

        val clientAddress = forwardedAddresses.asReversed()
            .firstOrNull { candidate -> trustedNetworks.none { it.contains(candidate) } }
            ?: return directAddress
        return InetSocketAddress(clientAddress, directAddress.port)
    }

    private fun forwardedForValues(request: ServerHttpRequest): List<String> {
        val forwarded = request.headers.getFirst("Forwarded")
        if (forwarded != null) {
            return forwarded.split(',').mapNotNull { element ->
                element.split(';')
                    .map(String::trim)
                    .firstOrNull { it.startsWith("for=", ignoreCase = true) }
                    ?.substringAfter('=')
            }
        }
        return request.headers.getFirst("X-Forwarded-For")
            ?.split(',')
            ?.map(String::trim)
            ?: emptyList()
    }

    private fun parseAddressLiteral(value: String): InetAddress? {
        var candidate = value.trim().removeSurrounding("\"")
        if (candidate.startsWith("[") && candidate.contains(']')) {
            candidate = candidate.substringAfter('[').substringBefore(']')
        } else if (candidate.count { it == ':' } == 1 && candidate.contains('.')) {
            candidate = candidate.substringBefore(':')
        }
        if (!IP_LITERAL_PATTERN.matches(candidate)) {
            return null
        }
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
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

    companion object {
        private val IP_LITERAL_PATTERN = Regex("[0-9A-Fa-f:.]+")
    }
}
