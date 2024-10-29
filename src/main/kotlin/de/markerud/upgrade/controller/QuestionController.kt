package de.markerud.upgrade.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus.OK
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@RestController
class QuestionController(
    private val webClient: WebClient,
    @Value("\${MOCK_BACKEND}")
    private val backend: String
) {

    @GetMapping("/question-controller")
    @ResponseStatus(OK)
    fun question(): Mono<String> {
        return webClient.get()
            .uri("$backend/question")
            .retrieve()
            .bodyToMono(String::class.java)
    }

}
