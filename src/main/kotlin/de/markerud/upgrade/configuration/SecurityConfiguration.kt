package de.markerud.upgrade.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.GET
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers

@EnableWebFluxSecurity
@Configuration
class SecurityConfiguration {

    @Bean
    fun securityFilterChain(
        http: ServerHttpSecurity
    ) = http {
        // Disable CSRF
        csrf { disable() }

        // Permit unauthorized routing to defined endpoints
        // require authentication for everything else
        authorizeExchange {
            authorize(pathMatchers(GET, "/question-route"), permitAll)
            authorize(pathMatchers(GET, "/question-controller"), permitAll)
            authorize(pathMatchers(GET, "/remove-request-header-public"), permitAll)
            authorize(anyExchange, authenticated)
        }

        oauth2ResourceServer {
            jwt { }
        }
    }

}
