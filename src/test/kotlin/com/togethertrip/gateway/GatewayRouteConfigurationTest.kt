package com.togethertrip.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayRouteConfigurationTest {

    @Test
    fun `기본 라우트는 업로드 정적 파일을 main 서비스로 전달한다`() {
        val routes = loadRoutes("application.yml")

        assertTrue(
            routes.any {
                it.id == "uploads-static" &&
                    it.uri == "http://main:8081" &&
                    it.predicates.contains(
                        "Path=/uploads/post-attachments/**,/uploads/user-profile-images/**",
                    )
            },
        )
    }

    @Test
    fun `운영 라우트는 업로드 정적 파일을 main 서비스로 전달한다`() {
        val routes = loadRoutes("application-prod.yml")

        assertTrue(
            routes.any {
                it.id == "uploads-static" &&
                    it.uri == "\${MAIN_SERVICE_URL}" &&
                    it.predicates.contains(
                        "Path=/uploads/post-attachments/**,/uploads/user-profile-images/**",
                    )
            },
        )
    }

    private fun loadRoutes(resourceName: String): List<RouteDefinition> {
        val properties = YamlPropertiesFactoryBean().apply {
            setResources(ClassPathResource(resourceName))
        }.getObject() ?: error("failed to load $resourceName")

        val routes = mutableListOf<RouteDefinition>()
        var index = 0
        while (true) {
            val prefix = "spring.cloud.gateway.server.webflux.routes[$index]"
            val id = properties.getProperty("$prefix.id") ?: break
            val uri = properties.getProperty("$prefix.uri") ?: error("route $id uri is missing")
            val predicates = mutableListOf<String>()
            var predicateIndex = 0
            while (true) {
                val predicate = properties.getProperty("$prefix.predicates[$predicateIndex]") ?: break
                predicates.add(predicate)
                predicateIndex += 1
            }
            routes.add(RouteDefinition(id = id, uri = uri, predicates = predicates))
            index += 1
        }

        assertEquals(4, routes.size)
        return routes
    }

    private data class RouteDefinition(
        val id: String,
        val uri: String,
        val predicates: List<String>,
    )
}
