package de.markerud.upgrade.configuration

import brave.http.HttpTracing
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.netty.http.brave.ReactorNettyHttpTracing
import reactor.netty.http.client.HttpClient
import reactor.netty.http.server.HttpServer

@Configuration
class TracingConfiguration {

    /**
     * Creates a tracing object for adding spanIds and traceIds to [HttpClient] and
     * [HttpServer] logs.
     */
    @Bean
    fun reactorNettyHttpTracing(httpTracing: HttpTracing): ReactorNettyHttpTracing =
        ReactorNettyHttpTracing.create(httpTracing)

}
