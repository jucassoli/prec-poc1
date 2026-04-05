package br.com.precatorios

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrecatoriosApiApplication

fun main(args: Array<String>) {
    runApplication<PrecatoriosApiApplication>(*args)
}
