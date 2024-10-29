package de.markerud.upgrade

import org.apache.http.HttpStatus.SC_OK
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt

class RemoveRequestHeaderTest: AbstractTestBase() {

    @Test
    fun `request to protected route`() {
        BACKEND.respondToAnyRequest(SC_OK)

        testClient
            .mutateWith(mockJwt())
            .get().uri("/remove-request-header-protected")
            .header("x-remove-me", "value-to-be-removed")
            .header("x-keep-me", "value-to-be-kept")
            .exchange()
            .expectStatus().isOk

        val sentRequest = BACKEND.recordedRequest()
        assertThat(sentRequest.containsHeader("x-keep-me")).isTrue()
        assertThat(sentRequest.containsHeader("x-remove-me")).isFalse()
    }

    @Test
    fun `request to public route`() {
        BACKEND.respondToAnyRequest(SC_OK)

        testClient
            .get().uri("/remove-request-header-public")
            .header("x-remove-me", "value-to-be-removed")
            .header("x-keep-me", "value-to-be-kept")
            .exchange()
            .expectStatus().isOk

        val sentRequest = BACKEND.recordedRequest()
        assertThat(sentRequest.containsHeader("x-keep-me")).isTrue()
        assertThat(sentRequest.containsHeader("x-remove-me")).isFalse()
    }

}
