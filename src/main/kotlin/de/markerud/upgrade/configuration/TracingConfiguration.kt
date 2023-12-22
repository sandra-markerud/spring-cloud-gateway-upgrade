package de.markerud.upgrade.configuration

import io.micrometer.context.ContextRegistry
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import reactor.netty.Metrics

@Configuration
class TracingConfiguration(
    private val observationRegistry: ObservationRegistry,
    private val tracer: Tracer
) {

    @PostConstruct
    fun postConstruct() {
        Hooks.enableAutomaticContextPropagation()
        ContextRegistry.getInstance().registerThreadLocalAccessor(
            ObservationAwareSpanThreadLocalAccessor(tracer)
        )
        ObservationThreadLocalAccessor.getInstance().observationRegistry =
            observationRegistry
        Metrics.observationRegistry(observationRegistry)
    }

    @Bean
    fun contextSnapshotFactory(): ContextSnapshotFactory =
        ContextSnapshotFactory.builder().build()

}
