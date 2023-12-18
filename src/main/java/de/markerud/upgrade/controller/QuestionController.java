package de.markerud.upgrade.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.OK;

@RestController
public class QuestionController {

    private final WebClient webClient;
    private final String backend;

    public QuestionController(WebClient webClient, @Value("${MOCK_BACKEND}") String backend) {
        this.webClient = webClient;
        this.backend = backend;
    }

    @GetMapping("/question-controller")
    @ResponseStatus(OK)
    public Mono<String> question() {
        return webClient.get().uri(backend + "/question")
                .retrieve()
                .bodyToMono(String.class);
    }

}
