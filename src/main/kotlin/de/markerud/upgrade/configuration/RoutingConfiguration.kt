package de.markerud.upgrade.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.GET
import java.net.URI

@Configuration
class RoutingConfiguration(
    @Value("\${MOCK_BACKEND}") private val mockBackend: URI
) {

    @Bean
    fun routeLocator(builder: RouteLocatorBuilder): RouteLocator = builder.routes {
        route(id = "answer_route") {
            path("/question-route") and method(GET)
            filters {
                rewritePath("/question-route", "/question")
            }
            uri(mockBackend)
        }
    }

}
