package de.markerud.upgrade;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.markerud.upgrade.filter.LoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.springtest.MockServerTest;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.zalando.logbook.Logbook;

import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@MockServerTest("MOCK_BACKEND=http://localhost:${mockServerPort}")
@SuppressWarnings({"SpringBootApplicationProperties", "unused"})
public abstract class TestBase {

    private static final String ACCESS_LOG_LOGGER_NAME = "reactor.netty.http.server.AccessLog";

    private MockServerClient mockBackend;

    private ListAppender<ILoggingEvent> appender;

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
    private final Logger accessLogLogger = (Logger) LoggerFactory.getLogger(ACCESS_LOG_LOGGER_NAME);
    private final Logger logbookLogger = (Logger) LoggerFactory.getLogger(Logbook.class);

    @LocalServerPort
    protected int serverPort = -1;

    @BeforeEach
    void setup() {
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        accessLogLogger.addAppender(appender);
        logbookLogger.addAppender(appender);
    }

    @AfterEach
    void tearDownLogAppender() {
        filterLogger.detachAppender(appender);
        accessLogLogger.detachAppender(appender);
        logbookLogger.detachAppender(appender);
    }

    @RepeatedTest(10)
    void traceAndSpanIDsAppearInApplicationLogs() {
        sendRequest();

        assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(filterLogger.getName()))
                .hasSize(1)
                .singleElement()
                .matches(event -> event.getMDCPropertyMap().containsKey("traceId"), "expected filter log to contain traceId")
                .matches(event -> event.getMDCPropertyMap().containsKey("spanId"), "expected filter log to contain spanId");
    }

    @RepeatedTest(10)
    void accessLogIsWritten() {
        sendRequest();

        assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(accessLogLogger.getName()))
                .hasSize(1);
    }

    @RepeatedTest(10)
    void traceAndSpanIDsAppearInAccessLogs() {
        sendRequest();

        assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(accessLogLogger.getName()))
                .hasSize(1)
                .singleElement()
                .matches(event -> event.getMDCPropertyMap().containsKey("traceId"), "expected access log to contain traceId")
                .matches(event -> event.getMDCPropertyMap().containsKey("spanId"), "expected access log to contain spanId");
    }

    @RepeatedTest(10)
    void traceAndSpanIDsAppearInZalandoLogbookLogs() {
        sendRequest();

        assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(logbookLogger.getName()))
                .allMatch(event -> event.getMDCPropertyMap().containsKey("traceId"), "expected logbook log to contain traceId")
                .allMatch(event -> event.getMDCPropertyMap().containsKey("spanId"), "expected logbook log to contain spanId");
    }

    @RepeatedTest(10)
    void allZalandoLogbookLogsAreWritten() {
        sendRequest();

        assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(logbookLogger.getName()))
                .hasSize(4);
    }

    @RepeatedTest(10)
    void zalandoLogbookLogsAreWrittenInTheCorrectOrder() {
        sendRequest();

        var logbookMessages = appender.list
                .stream()
                .filter(event -> event.getLoggerName().equals(logbookLogger.getName()))
                .map(ILoggingEvent::getMessage)
                .toList();


        assertThat(logbookMessages).hasSize(4);
        assertThat(logbookMessages.get(0)).startsWith("Incoming Request:");
        assertThat(logbookMessages.get(1)).startsWith("Outgoing Request:");
        assertThat(logbookMessages.get(2)).startsWith("Incoming Response:");
        assertThat(logbookMessages.get(3)).startsWith("Outgoing Response:");
    }

    @RepeatedTest(10)
    void tracingHeadersAreSentToDownstreamServices() {
        var sentRequest = sendRequest();

        // propagation type 'w3c'
        assertThat(sentRequest.containsHeader("traceparent"))
                .describedAs("expected header 'traceparent' to be present").isTrue();

        // propagation type 'b3'
        assertThat(sentRequest.containsHeader("X-B3-TraceId"))
                .describedAs("expected header 'X-B3-TraceId' to be present").isTrue();
        assertThat(sentRequest.containsHeader("X-B3-SpanId"))
                .describedAs("expected header 'X-B3-SpanId' to be present").isTrue();
        assertThat(sentRequest.containsHeader("X-B3-Sampled"))
                .describedAs("expected header 'X-B3-Sampled' to be present").isTrue();
        assertThat(sentRequest.containsHeader("X-B3-ParentSpanId"))
                .describedAs("expected header 'X-B3-ParentSpanId' to be present").isTrue();
    }

    protected abstract HttpRequest sendRequest();

    protected void mockBackendRespondOK() {
        mockBackend.when(request()).respond(response().withStatusCode(SC_OK));
    }

    protected HttpRequest getSentRequest() {
        var allRequests = mockBackend.retrieveRecordedRequests(null);
        return allRequests[allRequests.length - 1];
    }
}
