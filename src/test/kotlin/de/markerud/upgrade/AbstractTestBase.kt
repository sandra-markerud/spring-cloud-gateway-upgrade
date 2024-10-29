package de.markerud.upgrade

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import de.markerud.upgrade.AbstractTestBase.Companion.BACKEND_PORT
import de.markerud.upgrade.filter.LoggingFilter
import org.apache.http.HttpStatus.SC_OK
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.junit.jupiter.MockServerSettings
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import org.zalando.logbook.Logbook

@Suppress("SpringBootApplicationProperties")
@AutoConfigureObservability
@AutoConfigureWebTestClient
@MockServerSettings
@SpringBootTest(
    webEnvironment = RANDOM_PORT, properties = [
        "MOCK_BACKEND=http://localhost:$BACKEND_PORT"
    ]
)
abstract class AbstractTestBase {

    lateinit var appender: ListAppender<ILoggingEvent>

    val filterLogger = LoggerFactory.getLogger(LoggingFilter::class.java) as Logger
    val accessLogLogger = LoggerFactory.getLogger(ACCESS_LOG_LOGGER_NAME) as Logger
    val logbookLogger = LoggerFactory.getLogger(Logbook::class.java) as Logger

    @Autowired
    lateinit var testClient: WebTestClient

    @LocalServerPort
    val serverPort = -1

    val webClient: WebTestClient by lazy {
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:$serverPort")
            .build()
    }

    @BeforeEach
    fun setup() {
        appender = ListAppender()
        appender.start()
        filterLogger.addAppender(appender)
        accessLogLogger.addAppender(appender)
        logbookLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDownLogAppender() {
        filterLogger.detachAppender(appender)
        accessLogLogger.detachAppender(appender)
        logbookLogger.detachAppender(appender)
    }

    fun sendRequest(path: String) {
        mockBackendRespondOK()
        webClient
            .get().uri(path)
            .exchange()
            .expectStatus().isOk
    }

    fun mockBackendRespondOK() {
        BACKEND.`when`(request())
            .respond(response().withStatusCode(SC_OK))
    }

    companion object {
        const val BACKEND_PORT = 12000
        val BACKEND: ClientAndServer = ClientAndServer.startClientAndServer(BACKEND_PORT)
        private const val ACCESS_LOG_LOGGER_NAME = "reactor.netty.http.server.AccessLog"
    }

}

fun MockServerClient.whenever(request: HttpRequest, times: Times = Times.unlimited()) =
    this.`when`(request, times)!!

fun MockServerClient.recordedRequest(): HttpRequest =
    this.retrieveRecordedRequests(null).last()

fun MockServerClient.respondToAnyRequest(statusCode: Int) {
    whenever(request()).respond(response().withStatusCode(statusCode))
}
