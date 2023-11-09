package de.markerud.testapp;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements WebFilter {

    private final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @NotNull
    @Override
    public Mono<Void> filter(@NotNull ServerWebExchange exchange, WebFilterChain chain) {
        log.info("Some business logging...");
        return chain.filter(exchange);
    }

}
