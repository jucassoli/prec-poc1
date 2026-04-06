package br.com.precatorios

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PrecatoriosApiApplication

fun main(args: Array<String>) {
    runApplication<PrecatoriosApiApplication>(*args)
}
