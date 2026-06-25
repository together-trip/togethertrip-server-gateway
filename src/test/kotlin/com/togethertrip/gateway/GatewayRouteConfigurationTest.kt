package com.togethertrip.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayRouteConfigurationTest {

    @Test
    fun `기본 라우트는 로컬 단독 실행용 localhost fallback을 가진다`() {
        val routes = loadRoutes("application.yml")

        assertRoute(
            routes = routes,
            id = "main",
            uri = "\${MAIN_SERVICE_URL:http://localhost:8081}",
            path = "Path=/api/**",
        )
        assertRoute(
            routes = routes,
            id = "uploads-static",
            uri = "\${MAIN_SERVICE_URL:http://localhost:8081}",
            path = "Path=/uploads/post-attachments/**,/uploads/user-profile-images/**",
        )
        assertRoute(
            routes = routes,
            id = "notification",
            uri = "\${NOTIFICATION_SERVICE_URL:http://localhost:8082}",
            path = "Path=/notification/**",
        )
        assertRoute(
            routes = routes,
            id = "chat",
            uri = "\${CHAT_SERVICE_URL:http://localhost:8083}",
            path = "Path=/chat/**",
        )
    }

    @Test
    fun `local profile은 기본 라우트를 잘못된 namespace로 덮어쓰지 않는다`() {
        val routes = loadRoutes("application-local.yml", expectedSize = 0)

        assertTrue(routes.isEmpty())
    }

    @Test
    fun `운영 라우트는 main notification chat uploads 경로를 등록한다`() {
        val routes = loadRoutes("application-prod.yml")

        assertRoute(
            routes = routes,
            id = "main",
            uri = "\${MAIN_SERVICE_URL}",
            path = "Path=/api/**",
        )
        assertRoute(
            routes = routes,
            id = "uploads-static",
            uri = "\${MAIN_SERVICE_URL}",
            path = "Path=/uploads/post-attachments/**,/uploads/user-profile-images/**",
        )
        assertRoute(
            routes = routes,
            id = "notification",
            uri = "\${NOTIFICATION_SERVICE_URL}",
            path = "Path=/notification/**",
        )
        assertRoute(
            routes = routes,
            id = "chat",
            uri = "\${CHAT_SERVICE_URL}",
            path = "Path=/chat/**",
        )
    }

    private fun assertRoute(
        routes: List<RouteDefinition>,
        id: String,
        uri: String,
        path: String,
    ) {
        assertTrue(
            routes.any {
                it.id == id &&
                    it.uri == uri &&
                    it.predicates.contains(path)
            },
        )
    }

    private fun loadRoutes(
        resourceName: String,
        expectedSize: Int = 4,
    ): List<RouteDefinition> {
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

        assertEquals(expectedSize, routes.size)
        return routes
    }

    private data class RouteDefinition(
        val id: String,
        val uri: String,
        val predicates: List<String>,
    )
}
