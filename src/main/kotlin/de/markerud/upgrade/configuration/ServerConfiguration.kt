package de.markerud.upgrade.configuration

import io.micrometer.context.ContextSnapshotFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookServerHandler
import reactor.netty.Connection

@Configuration
class ServerConfiguration(
    @Value("\${logbook.filter.enabled:false}") private val logbookEnabled: Boolean,
    private val contextSnapshotFactory: ContextSnapshotFactory,
    private val logbook: Logbook
) {

    @Bean
    fun defaultNettyServerCustomizer(): NettyServerCustomizer =
        NettyServerCustomizer { server -> server
            .accessLog(true)
            .metrics(true) { uriTagValue: String -> uriTagValue }
            .doOnConnection { connection: Connection ->
                connection.addHandlerLast(tracingChannelDuplexHandler())
            }
        }

    private fun tracingChannelDuplexHandler(): TracingChannelDuplexHandler {
        val delegate = if (logbookEnabled) LogbookServerHandler(logbook) else null
        return TracingChannelDuplexHandler(delegate, contextSnapshotFactory)
    }

}
