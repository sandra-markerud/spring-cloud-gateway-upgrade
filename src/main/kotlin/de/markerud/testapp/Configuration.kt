package de.markerud.testapp

import brave.Tracer
import brave.http.HttpTracing
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.cloud.gateway.config.HttpClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.CorrelationId
import org.zalando.logbook.DefaultCorrelationId
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookClientHandler
import reactor.netty.http.brave.ReactorNettyHttpTracing
import reactor.netty.http.client.HttpClient
import reactor.netty.http.server.HttpServer

@Configuration
class Configuration {

    /**
     * Logbook CorrelationId reusing Brave TraceId/SpanId.
     * As the logbook filter has lower precedence than the sleuth filter, we can assume
     * that the tracing context is already set. In case the tracing context is not yet
     * set, e.g. for server-to-server communication, the [DefaultCorrelationId] is used.
     */
    @Bean
    fun tracingCorrelationId(tracer: Tracer): CorrelationId = CorrelationId { request ->
        tracer.currentSpan()
            ?.context()?.toString()
            ?: DefaultCorrelationId().generate(request)
    }

    /**
     * Creates a tracing object for adding spanIds and traceIds to [HttpClient] and
     * [HttpServer] logs.
     */
    @Bean
    fun reactorTracing(httpTracing: HttpTracing): ReactorNettyHttpTracing =
        ReactorNettyHttpTracing.create(httpTracing)

    /**
     * Configures the global [HttpClient] so outgoing requests and incoming responses are
     * logged by [Logbook].
     */
    @Bean
    fun logbookClientCustomizer(
        logbook: Logbook,
        reactorTracing: ReactorNettyHttpTracing
    ): HttpClientCustomizer = HttpClientCustomizer { httpClient ->
        reactorTracing.decorateHttpClient(httpClient).doOnConnected { conn ->
            conn.addHandlerLast(LogbookClientHandler(logbook))
        }
    }

    /**
     * Configures the global [HttpServer] so the traceId and spanId are visible in
     * Logbook logs.
     */
    @Bean
    fun logbookServerCustomizer(
        reactorTracing: ReactorNettyHttpTracing
    ): NettyServerCustomizer = NettyServerCustomizer { server ->
        reactorTracing.decorateHttpServer(server)
    }

}
