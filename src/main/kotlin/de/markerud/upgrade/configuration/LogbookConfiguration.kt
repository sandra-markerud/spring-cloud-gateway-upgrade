package de.markerud.upgrade.configuration

import brave.Tracer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.CorrelationId
import org.zalando.logbook.DefaultCorrelationId

@Configuration
@ConditionalOnProperty(value = ["logbook.filter.enabled"], havingValue = "true")
class LogbookConfiguration {

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

}
