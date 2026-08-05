package com.togethertrip.gateway.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import java.net.URI

@Component
@ConfigurationProperties(prefix = "gateway.cors")
class GatewayCorsProperties {
    var allowedOrigins: List<String> = listOf("http://localhost:3000")
    var maxAgeSeconds: Long = 3600
    var requireHttpsOrigins: Boolean = false
}

@Configuration
class GatewayCorsConfiguration(
    private val properties: GatewayCorsProperties,
) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        check(properties.allowedOrigins.isNotEmpty()) { "gateway.cors.allowed-origins must not be empty" }
        check("*" !in properties.allowedOrigins) { "gateway.cors.allowed-origins must not contain wildcard" }
        properties.allowedOrigins.forEach(::validateOrigin)

        val configuration = CorsConfiguration().apply {
            allowedOrigins = properties.allowedOrigins
            allowedMethods = listOf(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
            )
            allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, REQUEST_ID_HEADER)
            exposedHeaders = listOf(REQUEST_ID_HEADER)
            allowCredentials = true
            maxAge = properties.maxAgeSeconds
        }
        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
        return CorsWebFilter(source)
    }

    private fun validateOrigin(origin: String) {
        val uri = runCatching { URI(origin) }
            .getOrElse { error("gateway.cors.allowed-origins contains invalid URI: $origin") }
        check(uri.host != null && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
            "gateway.cors.allowed-origins must contain origin only: $origin"
        }
        if (properties.requireHttpsOrigins) {
            check(uri.scheme.equals("https", ignoreCase = true)) {
                "gateway.cors.allowed-origins must use HTTPS: $origin"
            }
        }
    }

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
