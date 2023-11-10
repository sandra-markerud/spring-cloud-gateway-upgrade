package de.markerud.upgrade;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.restassured.RestAssured;
import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.springtest.MockServerTest;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.zalando.logbook.Logbook;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.apache.http.HttpStatus.SC_OK;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@MockServerTest("MOCK_BACKEND=http://localhost:${mockServerPort}")
@SuppressWarnings("SpringBootApplicationProperties")
public class ApplicationTests {

    private static final String ACCESS_LOG_LOGGER_NAME = "reactor.netty.http.server.AccessLog";

    private MockServerClient mockBackend;

    private ListAppender<ILoggingEvent> appender;

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
    private final Logger accessLogLogger = (Logger) LoggerFactory.getLogger(ACCESS_LOG_LOGGER_NAME);
    private final Logger logbookLogger = (Logger) LoggerFactory.getLogger(Logbook.class);

    @LocalServerPort
    private int serverPort = -1;

    @BeforeAll
    static void activateAccessLog() {
        System.setProperty("reactor.netty.http.server.accessLogEnabled", "true");
    }

    @BeforeEach
    void setupLogAppender() {
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

    @Test
    void testLoggingAndTracing() {
        mockBackendRespondOK();

        RestAssured
                .given().when()
                .port(serverPort)
                .get("/question")
                .then()
                .statusCode(SC_OK);


        SoftAssertions softly = new SoftAssertions();

        assertThatTraceAndSpanIdsAreSentToDownstreamServices(softly);
        assertThatAccessLogContainsTraceAndSpanIds(softly);
        assertThatFilterLogEntryContainsTraceAndSpanIds(softly);
        assertThatLogbookLogEntriesContainsTraceAndSpanIds(softly);
        assertThatAllExpectedLogbookEntriesArePresent(softly);

        softly.assertAll();
    }

    private void mockBackendRespondOK() {
        mockBackend.when(request()).respond(response().withStatusCode(SC_OK));
    }

    private HttpRequest getSentRequest() {
        var allRequests = mockBackend.retrieveRecordedRequests(null);
        return allRequests[allRequests.length - 1];
    }

    private void assertThatTraceAndSpanIdsAreSentToDownstreamServices(SoftAssertions softly) {
        var sentRequest = getSentRequest();

        // propagation type 'w3c'
        softly.assertThat(sentRequest.containsHeader("traceparent"))
                .describedAs("expected header 'traceparent' to be present").isTrue();

        // propagation type 'b3'
        softly.assertThat(sentRequest.containsHeader("X-B3-TraceId"))
                .describedAs("expected header 'X-B3-TraceId' to be present").isTrue();
        softly.assertThat(sentRequest.containsHeader("X-B3-SpanId"))
                .describedAs("expected header 'X-B3-SpanId' to be present").isTrue();
        softly.assertThat(sentRequest.containsHeader("X-B3-Sampled"))
                .describedAs("expected header 'X-B3-Sampled' to be present").isTrue();
        softly.assertThat(sentRequest.containsHeader("X-B3-ParentSpanId"))
                .describedAs("expected header 'X-B3-ParentSpanId' to be present").isTrue();
    }

    private void assertThatAccessLogContainsTraceAndSpanIds(SoftAssertions softly) {
        softly.assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(accessLogLogger.getName()))
                .hasSize(1)
                .singleElement()
                .matches(event -> event.getMDCPropertyMap().containsKey("traceId"), "expected access log to contain traceId")
                .matches(event -> event.getMDCPropertyMap().containsKey("spanId"), "expected access log to contain spanId");
    }

    private void assertThatFilterLogEntryContainsTraceAndSpanIds(SoftAssertions softly) {
        softly.assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(filterLogger.getName()))
                .hasSize(1)
                .singleElement()
                .matches(event -> event.getMDCPropertyMap().containsKey("traceId"), "expected filter log to contain traceId")
                .matches(event -> event.getMDCPropertyMap().containsKey("spanId"), "expected filter log to contain spanId");
    }


    private void assertThatLogbookLogEntriesContainsTraceAndSpanIds(SoftAssertions softly) {
        softly.assertThat(appender.list)
                .filteredOn(event -> event.getLoggerName().equals(logbookLogger.getName()))
                .allMatch(event -> event.getMDCPropertyMap().containsKey("traceId"), "expected logbook log to contain traceId")
                .allMatch(event -> event.getMDCPropertyMap().containsKey("spanId"), "expected logbook log to contain spanId");
    }

    private void assertThatAllExpectedLogbookEntriesArePresent(SoftAssertions softly) {
        List<String> logbookMessages = appender.list
                .stream()
                .filter(event -> event.getLoggerName().equals(logbookLogger.getName()))
                .map(ILoggingEvent::getMessage)
                .collect(toList());

        softly.assertThat(logbookMessages)
                .hasSize(4)
                .haveExactly(1, messageStartingWith("Incoming Request:"))
                .haveExactly(1, messageStartingWith("Outgoing Request:"))
                .haveExactly(1, messageStartingWith("Incoming Response:"))
                .haveExactly(1, messageStartingWith("Outgoing Response:"));
    }

    private Condition<String> messageStartingWith(String message) {
        return new Condition<>(msg -> msg.startsWith(message), "starts with %s", message);
    }

}
