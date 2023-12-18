package de.markerud.upgrade.configuration;

import brave.Tracer;
import brave.http.HttpTracing;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.CorrelationId;
import org.zalando.logbook.DefaultCorrelationId;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;
import org.zalando.logbook.netty.LogbookServerHandler;
import reactor.netty.http.brave.ReactorNettyHttpTracing;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

@Configuration
public class ObservabilityConfiguration {

    /**
     * Logbook CorrelationId reusing Brave TraceId/SpanId.
     * As the logbook filter has lower precedence than the sleuth filter, we can assume
     * that the tracing context is already set. In case the tracing context is not yet
     * set, e.g. for server-to-server communication, the [DefaultCorrelationId] is used.
     */
    @Bean
    public CorrelationId tracingCorrelationId(Tracer tracer) {
        return request -> {
            if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
                return tracer.currentSpan().context().toString();
            } else {
                return new DefaultCorrelationId().generate(request);
            }
        };
    }

    /**
     * Creates a tracing object for adding spanIds and traceIds to [HttpClient] and
     * [HttpServer] logs.
     */
    @Bean
    public ReactorNettyHttpTracing reactorNettyHttpTracing(HttpTracing httpTracing) {
        return ReactorNettyHttpTracing.create(httpTracing);
    }

    /**
     * Configures the global {@link HttpServer} so incoming requests and outgoing responses are
     * logged by [Logbook] and traceId and spanId are visible within these logs.
     */
    @Bean
    NettyServerCustomizer nettyServerCustomizer(Logbook logbook, ReactorNettyHttpTracing reactorNettyHttpTracing) {
        return server -> reactorNettyHttpTracing
                .decorateHttpServer(server)
                .doOnConnection(conn -> conn.addHandlerFirst(new LogbookServerHandler(logbook)));
    }

    /**
     * Configures the global {@link HttpClient} so outgoing requests and incoming responses are
     * logged by [Logbook] and traceId and spanId are visible within these logs.
     */
    @Bean
    public HttpClientCustomizer logbookClientCustomizer(Logbook logbook, ReactorNettyHttpTracing reactorTracing) {
        return httpClient -> reactorTracing
                .decorateHttpClient(httpClient)
                .doOnConnected(conn -> conn.addHandlerLast(new LogbookClientHandler(logbook)));
    }

    @Bean
    WebClient webClient(Logbook logbook, ReactorNettyHttpTracing reactorNettyHttpTracing) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactorNettyHttpTracing.decorateHttpClient(HttpClient
                        .create().doOnConnected(conn -> conn.addHandlerLast(new LogbookClientHandler(logbook))))))
                .build();
    }

}
