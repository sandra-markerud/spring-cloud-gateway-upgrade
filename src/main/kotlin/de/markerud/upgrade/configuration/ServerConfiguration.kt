package de.markerud.upgrade.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookServerHandler
import reactor.netty.http.brave.ReactorNettyHttpTracing

@Configuration
class ServerConfiguration {

    @Bean
    fun nettyServerAccessLogCustomizer(): NettyServerCustomizer =
        NettyServerCustomizer { server -> server.accessLog(true) }

    @Bean
    @ConditionalOnProperty(
        value = ["logbook.filter.enabled"],
        havingValue = "false",
        matchIfMissing = true
    )
    fun nettyServerTracingCustomizer(
        reactorNettyHttpTracing: ReactorNettyHttpTracing
    ): NettyServerCustomizer = NettyServerCustomizer { server ->
        reactorNettyHttpTracing.decorateHttpServer(server)
    }

    @Bean
    @ConditionalOnProperty(value = ["logbook.filter.enabled"], havingValue = "true")
    fun logbookNettyServerTracingCustomizer(
        logbook: Logbook,
        reactorNettyHttpTracing: ReactorNettyHttpTracing
    ): NettyServerCustomizer = NettyServerCustomizer { server ->
        reactorNettyHttpTracing
            .decorateHttpServer(server)
            .doOnConnection { conn -> conn.addHandlerFirst(LogbookServerHandler(logbook)) }
    }

}
