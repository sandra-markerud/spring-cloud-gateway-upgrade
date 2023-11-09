package de.markerud.upgrade;

import brave.Tracer;
import brave.http.HttpTracing;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.CorrelationId;
import org.zalando.logbook.DefaultCorrelationId;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;
import reactor.netty.http.brave.ReactorNettyHttpTracing;

@Configuration
public class Config {

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
    public ReactorNettyHttpTracing reactorTracing(HttpTracing httpTracing) {
        return ReactorNettyHttpTracing.create(httpTracing);
    }

    /**
     * Configures the global [HttpClient] so outgoing requests and incoming responses are
     * logged by [Logbook].
     */
    @Bean
    public HttpClientCustomizer logbookClientCustomizer(Logbook logbook, ReactorNettyHttpTracing reactorTracing) {
        return httpClient -> reactorTracing
                .decorateHttpClient(httpClient)
                .doOnConnected(conn -> conn.addHandlerLast(new LogbookClientHandler(logbook)));
    }

    /**
     * Configures the global [HttpServer] so the traceId and spanId are visible in
     * Logbook logs.
     */
    @Bean
    public NettyServerCustomizer logbookServerCustomizer(ReactorNettyHttpTracing reactorTracing) {
        return reactorTracing::decorateHttpServer;
    }

}
