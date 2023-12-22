package de.markerud.upgrade.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.gateway.config.HttpClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookClientHandler
import reactor.netty.http.brave.ReactorNettyHttpTracing
import reactor.netty.http.client.HttpClient

@Configuration
class ClientConfiguration {

    @Bean
    fun nettyClientTracingCustomizer(
        reactorNettyHttpTracing: ReactorNettyHttpTracing
    ): HttpClientCustomizer = HttpClientCustomizer { client ->
        reactorNettyHttpTracing.decorateHttpClient(client)
    }

    @Bean
    @ConditionalOnProperty(value = ["logbook.filter.enabled"], havingValue = "true")
    fun nettyClientLogbookCustomizer(logbook: Logbook): HttpClientCustomizer =
        HttpClientCustomizer { client ->
            client.doOnConnected { connection ->
                connection.addHandlerLast(LogbookClientHandler(logbook))
            }
        }

    @Bean
    @ConditionalOnProperty(
        value = ["logbook.filter.enabled"],
        havingValue = "false",
        matchIfMissing = true
    )
    fun defaultWebClient(webClientBuilder: WebClient.Builder): WebClient =
        webClientBuilder.build()

    @Bean
    @ConditionalOnProperty(value = ["logbook.filter.enabled"], havingValue = "true")
    fun webClient(
        logbook: Logbook,
        reactorNettyHttpTracing: ReactorNettyHttpTracing
    ): WebClient {
        return WebClient.builder()
            .clientConnector(
                ReactorClientHttpConnector(
                    reactorNettyHttpTracing.decorateHttpClient(
                        HttpClient
                            .create().doOnConnected { conn ->
                                conn.addHandlerLast(LogbookClientHandler(logbook))
                            })
                )
            )
            .build()
    }

}
