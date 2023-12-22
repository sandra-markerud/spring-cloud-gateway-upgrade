package de.markerud.upgrade

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.zalando.logbook.autoconfigure.webflux.LogbookWebFluxAutoConfiguration

@SpringBootApplication(exclude = [LogbookWebFluxAutoConfiguration::class])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
