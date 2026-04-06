package br.com.precatorios.controller

import br.com.precatorios.exception.GlobalExceptionHandler
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.scraper.BuscaResultado
import br.com.precatorios.scraper.EsajScraper
import br.com.precatorios.scraper.IncidenteScraped
import br.com.precatorios.scraper.ParteScraped
import br.com.precatorios.scraper.ProcessoScraped
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ProcessoController::class)
@Import(GlobalExceptionHandler::class)
class ProcessoControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var esajScraper: EsajScraper

    private val validNumero = "1001234-56.2020.8.26.0100"

    private fun stubProcessoScraped(missingFields: List<String> = emptyList()) = ProcessoScraped(
        numero = validNumero,
        classe = "Procedimento Comum",
        assunto = "Precatorio",
        foro = "Foro Central",
        vara = "1a Vara da Fazenda Publica",
        juiz = "Dr. Teste Silva",
        valorAcao = "R$ 150.000,00",
        partes = listOf(
            ParteScraped(nome = "JOAO DA SILVA", tipo = "Reqte", advogado = "DR. ADVOGADO TESTE")
        ),
        incidentes = listOf(
            IncidenteScraped(numero = "INC001", descricao = "Embargos de Declaracao", link = null)
        ),
        missingFields = missingFields,
        rawHtml = "<html></html>"
    )

    @Test
    fun `GET processo by valid CNJ number returns 200 with full response`() {
        every { esajScraper.fetchProcesso(validNumero) } returns stubProcessoScraped()

        mockMvc.get("/api/v1/processo/{numero}", validNumero)
            .andExpect {
                status { isOk() }
                jsonPath("$.numero") { value(validNumero) }
                jsonPath("$.classe") { value("Procedimento Comum") }
                jsonPath("$.partes") { isArray() }
                jsonPath("$.partes[0].nome") { value("JOAO DA SILVA") }
                jsonPath("$.incidentes") { isArray() }
                jsonPath("$.missingFields") { isArray() }
                jsonPath("$.dadosCompletos") { value(true) }
            }
    }

    @Test
    fun `GET processo with invalid CNJ format returns 400`() {
        mockMvc.get("/api/v1/processo/{numero}", "INVALID-NUMBER")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET processo with missing fields returns dadosCompletos false`() {
        every { esajScraper.fetchProcesso(validNumero) } returns stubProcessoScraped(
            missingFields = listOf("valorAcao", "juiz")
        )

        mockMvc.get("/api/v1/processo/{numero}", validNumero)
            .andExpect {
                status { isOk() }
                jsonPath("$.dadosCompletos") { value(false) }
                jsonPath("$.missingFields[0]") { value("valorAcao") }
            }
    }

    @Test
    fun `GET processo buscar with nome returns 200 with results`() {
        every { esajScraper.buscarPorNome("Silva") } returns listOf(
            BuscaResultado(
                numero = validNumero,
                classe = "Procedimento Comum",
                assunto = "Precatorio",
                foro = "Foro Central"
            )
        )

        mockMvc.get("/api/v1/processo/buscar") {
            param("nome", "Silva")
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultados") { isArray() }
            jsonPath("$.resultados[0].numero") { value(validNumero) }
            jsonPath("$.total") { value(1) }
        }
    }

    @Test
    fun `GET processo buscar with no params returns 400`() {
        mockMvc.get("/api/v1/processo/buscar")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET processo when ScrapingException thrown returns 503`() {
        every { esajScraper.fetchProcesso(validNumero) } throws ScrapingException("e-SAJ unavailable")

        mockMvc.get("/api/v1/processo/{numero}", validNumero)
            .andExpect {
                status { isServiceUnavailable() }
            }
    }
}
