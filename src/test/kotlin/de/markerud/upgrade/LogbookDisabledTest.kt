package de.markerud.upgrade

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockserver.model.HttpRequest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(
    webEnvironment = RANDOM_PORT, properties = [
        "logbook.filter.enabled=false"
    ]
)
class LogbookDisabledTest : AbstractTestBase() {

    @ParameterizedTest
    @ValueSource(strings = ["/question-route", "/question-controller"])
    fun `trace and span IDs appear in application logs`(path: String) {
        sendRequest(path)
        assertThat(appender.list)
            .filteredOn { event -> event.loggerName == filterLogger.name }
            .hasSize(1)
            .singleElement()
            .matches(
                { event -> event.mdcPropertyMap.containsKey("traceId") },
                "expected controller log to contain traceId"
            )
            .matches(
                { event -> event.mdcPropertyMap.containsKey("spanId") },
                "expected controller log to contain spanId"
            )
    }

    @ParameterizedTest
    @ValueSource(strings = ["/question-route", "/question-controller"])
    fun `access log is written`(path: String) {
        sendRequest(path)
        assertThat(appender.list)
            .filteredOn { event -> event.loggerName == accessLogLogger.name }
            .hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/question-route", "/question-controller"])
    fun `trace and span IDs appear in access log`(path: String) {
        sendRequest(path)
        assertThat(appender.list)
            .filteredOn { event -> event.loggerName.equals(accessLogLogger.name) }
            .hasSize(1)
            .singleElement()
            .matches(
                { event -> event.mdcPropertyMap.containsKey("traceId") },
                "expected access log to contain traceId"
            )
            .matches(
                { event -> event.mdcPropertyMap.containsKey("spanId") },
                "expected access log to contain spanId"
            )
    }

    @ParameterizedTest
    @ValueSource(strings = ["/question-route", "/question-controller"])
    fun `no zalando logbook logs are written`(path: String) {
        sendRequest(path)
        assertThat(appender.list)
            .filteredOn { event -> event.loggerName.equals(logbookLogger.name) }
            .isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/question-route", "/question-controller"])
    fun `tracing headers are sent to downstream services`(path: String) {
        val sentRequest: HttpRequest = sendRequest(path)

        // propagation type 'w3c'
        assertThat(sentRequest.containsHeader("traceparent"))
            .describedAs("expected header 'traceparent' to be present").isTrue()

        // propagation type 'b3'
        assertThat(sentRequest.containsHeader("X-B3-TraceId"))
            .describedAs("expected header 'X-B3-TraceId' to be present").isTrue()
        assertThat(sentRequest.containsHeader("X-B3-SpanId"))
            .describedAs("expected header 'X-B3-SpanId' to be present").isTrue()
        assertThat(sentRequest.containsHeader("X-B3-Sampled"))
            .describedAs("expected header 'X-B3-Sampled' to be present").isTrue()
        assertThat(sentRequest.containsHeader("X-B3-ParentSpanId"))
            .describedAs("expected header 'X-B3-ParentSpanId' to be present").isTrue()
    }

}