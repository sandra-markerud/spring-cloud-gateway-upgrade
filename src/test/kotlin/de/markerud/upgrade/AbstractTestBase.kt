package de.markerud.upgrade

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import de.markerud.upgrade.filter.LoggingFilter
import io.restassured.RestAssured
import org.apache.http.HttpStatus.SC_OK
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.springtest.MockServerTest
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.zalando.logbook.Logbook

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@MockServerTest("MOCK_BACKEND=http://localhost:\${mockServerPort}")
@Suppress("SpringBootApplicationProperties", "unused")
abstract class AbstractTestBase {

    private lateinit var mockBackend: MockServerClient
    lateinit var appender: ListAppender<ILoggingEvent>
    
    val filterLogger = LoggerFactory.getLogger(LoggingFilter::class.java) as Logger
    val accessLogLogger = LoggerFactory.getLogger(ACCESS_LOG_LOGGER_NAME) as Logger
    val logbookLogger = LoggerFactory.getLogger(Logbook::class.java) as Logger

    @LocalServerPort
    val serverPort = -1

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

    fun sendRequest(path: String): HttpRequest {
        mockBackendRespondOK()
        RestAssured
            .given().`when`()
            .port(serverPort).get(path)
            .then()
            .statusCode(SC_OK)
        return sentRequest
    }

    private fun mockBackendRespondOK() {
        mockBackend.`when`(request())
            .respond(response().withStatusCode(SC_OK))
    }

    private val sentRequest: HttpRequest
        get() {
            val allRequests = mockBackend.retrieveRecordedRequests(null)
            return allRequests[allRequests.size - 1]
        }

    companion object {
        private const val ACCESS_LOG_LOGGER_NAME = "reactor.netty.http.server.AccessLog"
    }

}
