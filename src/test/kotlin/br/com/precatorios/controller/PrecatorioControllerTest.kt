package br.com.precatorios.controller

import br.com.precatorios.exception.GlobalExceptionHandler
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.scraper.CacScraper
import br.com.precatorios.scraper.PrecatorioScraped
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(PrecatorioController::class)
@Import(GlobalExceptionHandler::class)
class PrecatorioControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var cacScraper: CacScraper

    private fun stubPrecatorioScraped(missingFields: List<String> = emptyList()) = PrecatorioScraped(
        numeroPrecatorio = "0001234-56.2020.8.26.0100",
        numeroProcesso = "1001234-56.2020.8.26.0100",
        entidadeDevedora = "ESTADO DE SAO PAULO",
        valorOriginal = "R$ 100.000,00",
        valorAtualizado = "R$ 120.000,00",
        natureza = "Alimentar",
        statusPagamento = "Aguardando Pagamento",
        posicaoCronologica = 42,
        dataExpedicao = "01/01/2022",
        missingFields = missingFields,
        rawHtml = "<html></html>"
    )

    @Test
    fun `GET precatorio by number returns 200 with full response`() {
        val numero = "0001234-56.2020.8.26.0100"
        every { cacScraper.fetchPrecatorio(numero) } returns stubPrecatorioScraped()

        mockMvc.get("/api/v1/precatorio/{numero}", numero)
            .andExpect {
                status { isOk() }
                jsonPath("$.numeroPrecatorio") { value(numero) }
                jsonPath("$.entidadeDevedora") { value("ESTADO DE SAO PAULO") }
                jsonPath("$.valorOriginal") { value("R$ 100.000,00") }
                jsonPath("$.statusPagamento") { value("Aguardando Pagamento") }
                jsonPath("$.posicaoCronologica") { value(42) }
                jsonPath("$.dadosCompletos") { value(true) }
            }
    }

    @Test
    fun `GET precatorio when ScrapingException thrown returns 503`() {
        val numero = "0001234-56.2020.8.26.0100"
        every { cacScraper.fetchPrecatorio(numero) } throws ScrapingException("CAC unavailable")

        mockMvc.get("/api/v1/precatorio/{numero}", numero)
            .andExpect {
                status { isServiceUnavailable() }
            }
    }

    @Test
    fun `GET precatorio with partial data returns missingFields in response`() {
        val numero = "0001234-56.2020.8.26.0100"
        every { cacScraper.fetchPrecatorio(numero) } returns stubPrecatorioScraped(
            missingFields = listOf("valorAtualizado", "posicaoCronologica")
        )

        mockMvc.get("/api/v1/precatorio/{numero}", numero)
            .andExpect {
                status { isOk() }
                jsonPath("$.dadosCompletos") { value(false) }
                jsonPath("$.missingFields[0]") { value("valorAtualizado") }
            }
    }
}
