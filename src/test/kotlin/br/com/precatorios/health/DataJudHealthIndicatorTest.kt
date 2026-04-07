package br.com.precatorios.health

import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.scraper.DataJudClient
import br.com.precatorios.scraper.DataJudResult
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

class DataJudHealthIndicatorTest {

    private val dataJudClient = mockk<DataJudClient>()
    private val properties = ScraperProperties(
        datajud = ScraperProperties.DataJudProps(baseUrl = "https://api-publica.datajud.cnj.jus.br")
    )
    private val indicator = DataJudHealthIndicator(dataJudClient, properties)

    @Test
    fun `returns UP when DataJud responds successfully`() {
        every { dataJudClient.doBuscarPorNumero(any()) } returns DataJudResult(hits = emptyList(), total = 0)

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["url"]).isEqualTo("https://api-publica.datajud.cnj.jus.br")
    }

    @Test
    fun `returns DOWN when DataJud throws exception`() {
        every { dataJudClient.doBuscarPorNumero(any()) } throws ScrapingException("DataJud timeout")

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["error"]).isEqualTo("ScrapingException")
    }
}
