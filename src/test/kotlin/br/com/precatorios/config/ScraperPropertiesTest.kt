package br.com.precatorios.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@Testcontainers
class ScraperPropertiesTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    lateinit var properties: ScraperProperties

    @Test
    fun `esaj properties load correctly from application yml`() {
        assertThat(properties.esaj.baseUrl).isEqualTo("https://esaj.tjsp.jus.br")
        assertThat(properties.esaj.delayMs).isEqualTo(2000L)
        assertThat(properties.esaj.timeoutMs).isEqualTo(30000)
        assertThat(properties.esaj.maxRetries).isEqualTo(3)
    }

    @Test
    fun `precatorioPortal properties load correctly from application yml`() {
        assertThat(properties.precatorioPortal.baseUrl).isEqualTo("https://www.tjsp.jus.br/cac/scp")
        assertThat(properties.precatorioPortal.delayMs).isEqualTo(2000L)
        assertThat(properties.precatorioPortal.timeoutMs).isEqualTo(30000)
    }

    @Test
    fun `datajud properties load correctly from application yml`() {
        assertThat(properties.datajud.baseUrl).isEqualTo("https://api-publica.datajud.cnj.jus.br")
        assertThat(properties.datajud.endpointTjsp).isEqualTo("/api_publica_tjsp/_search")
        assertThat(properties.datajud.maxResultadosPorPagina).isEqualTo(100)
    }

    @Test
    fun `datajud apiKey is configured`() {
        assertThat(properties.datajud.apiKey).isNotBlank()
    }
}
