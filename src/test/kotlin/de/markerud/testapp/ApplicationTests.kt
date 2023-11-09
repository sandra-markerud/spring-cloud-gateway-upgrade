package de.markerud.testapp

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.restassured.RestAssured
import org.apache.http.HttpStatus.SC_OK
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.springtest.MockServerTest
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.zalando.logbook.Logbook
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties

@SpringBootTest(webEnvironment = RANDOM_PORT)
@MockServerTest("MOCK_BACKEND=http://localhost:\${mockServerPort}")
@ExtendWith(SystemStubsExtension::class)
@Suppress("SpringBootApplicationProperties")
class ApplicationTests {

    private lateinit var mockBackend: MockServerClient

    private lateinit var appender: ListAppender<ILoggingEvent>

    private val filterLogger =
        LoggerFactory.getLogger(LoggingFilter::class.java) as Logger
    private val accessLogLogger = LoggerFactory.getLogger(accessLogLoggerName) as Logger
    private val logbookLogger = LoggerFactory.getLogger(Logbook::class.java) as Logger

    @LocalServerPort
    var serverPort = -1

    @BeforeEach
    fun setup() {
        appender = ListAppender()
        appender.start()
        filterLogger.addAppender(appender)
        accessLogLogger.addAppender(appender)
        logbookLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        filterLogger.detachAppender(appender)
        accessLogLogger.detachAppender(appender)
        logbookLogger.detachAppender(appender)
    }

    @Test
    fun testLoggingAndTracing() {
        mockBackendRespondOK()

        RestAssured
            .given().`when`()
            .port(serverPort)
            .get("question")
            .then()
            .statusCode(SC_OK)

        assertTraceAndSpaIdsAreSentToDownstreamServices()
        assertThatAllLogEntriesContainTraceAndSpanIds()
        assertThatAllExpectedLogbookEntriesArePresent()
    }

    private fun mockBackendRespondOK() {
        mockBackend.`when`(request()).respond(response().withStatusCode(SC_OK))
    }

    private fun getSentRequest() = mockBackend.retrieveRecordedRequests(null).last()

    private fun assertTraceAndSpaIdsAreSentToDownstreamServices() {
        val sentRequest = getSentRequest()

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

    private fun assertThatAllLogEntriesContainTraceAndSpanIds() {
        assertThat(appender.list)
            .hasSize(6) // 4 logbook entries, 1 filter log and 1 access log
            .allMatch { it.mdcPropertyMap.containsKey("traceId") }
            .allMatch { it.mdcPropertyMap.containsKey("spanId") }
    }

    private fun assertThatAllExpectedLogbookEntriesArePresent() {
        val logbookMessages = appender.list
            .filter { it.loggerName == logbookLogger.name }
            .map { it.message }

        assertThat(logbookMessages)
            .hasSize(4)
            .haveExactly(1, messageStartingWith("Incoming Request:"))
            .haveExactly(1, messageStartingWith("Outgoing Request:"))
            .haveExactly(1, messageStartingWith("Incoming Response:"))
            .haveExactly(1, messageStartingWith("Outgoing Response:"))
    }

    private fun messageStartingWith(message: String): Condition<String> =
        Condition({ it.startsWith(message) }, "starts with %s", message)

    companion object {
        const val accessLogLoggerName = "reactor.netty.http.server.AccessLog"

        @SystemStub
        @Suppress("unused")
        val systemProperties: SystemProperties = SystemProperties(
            "reactor.netty.http.server.accessLogEnabled", "true"
        )
    }

}
