package de.markerud.upgrade.configuration

import io.micrometer.context.ContextSnapshotFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.web.reactive.function.client.ReactorNettyHttpClientMapper
import org.springframework.cloud.gateway.config.HttpClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookClientHandler
import reactor.netty.Connection

@Configuration
class ClientConfiguration(
    @Value("\${logbook.filter.enabled:false}") private val logbookEnabled: Boolean,
    private val contextSnapshotFactory: ContextSnapshotFactory,
    private val logbook: Logbook
) {

    @Bean
    fun reactorNettyHttpClientMapper() = ReactorNettyHttpClientMapper {
        it.doOnConnected { connection: Connection ->
            connection.addHandlerLast(tracingChannelDuplexHandler())
        }
    }

    @Bean
    fun httpClientCustomizer(
        contextSnapshotFactory: ContextSnapshotFactory
    ): HttpClientCustomizer = HttpClientCustomizer {
        it.doOnConnected { connection: Connection ->
            connection.addHandlerLast(tracingChannelDuplexHandler())
        }
    }

    @Bean
    fun webClient(webClientBuilder: WebClient.Builder): WebClient =
        webClientBuilder.build()

    private fun tracingChannelDuplexHandler(): TracingChannelDuplexHandler {
        val delegate = if (logbookEnabled) LogbookClientHandler(logbook) else null
        return TracingChannelDuplexHandler(delegate, contextSnapshotFactory)
    }

}
