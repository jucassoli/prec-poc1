package br.com.precatorios.controller

import br.com.precatorios.exception.GlobalExceptionHandler
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.scraper.DataJudClient
import br.com.precatorios.scraper.DataJudHit
import br.com.precatorios.scraper.DataJudResult
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(DataJudController::class)
@Import(GlobalExceptionHandler::class)
class DataJudControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dataJudClient: DataJudClient

    private fun stubDataJudResult() = DataJudResult(
        hits = listOf(
            DataJudHit(
                numeroProcesso = "1234567-89.2023.8.26.0100",
                classe = "Precatório",
                assunto = "Pagamento de precatório",
                orgaoJulgador = "1a Vara da Fazenda",
                dataAjuizamento = "2023-01-15",
                raw = "{}"
            )
        ),
        total = 1L
    )

    @Test
    fun `POST datajud buscar with numeroProcesso returns 200 with results`() {
        every { dataJudClient.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100") } returns stubDataJudResult()

        mockMvc.post("/api/v1/datajud/buscar") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"numeroProcesso":"1234567-89.2023.8.26.0100"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(1) }
            jsonPath("$.resultados") { isArray() }
            jsonPath("$.resultados[0].numeroProcesso") { value("1234567-89.2023.8.26.0100") }
            jsonPath("$.resultados[0].classe") { value("Precatório") }
        }
    }

    @Test
    fun `POST datajud buscar with codigoMunicipioIBGE returns 200 with results`() {
        every { dataJudClient.buscarPorMunicipio("3550308") } returns stubDataJudResult()

        mockMvc.post("/api/v1/datajud/buscar") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"codigoMunicipioIBGE":"3550308"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(1) }
            jsonPath("$.resultados") { isArray() }
        }
    }

    @Test
    fun `POST datajud buscar with empty body returns 400`() {
        mockMvc.post("/api/v1/datajud/buscar") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST datajud buscar when ScrapingException thrown returns 503`() {
        every { dataJudClient.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100") } throws ScrapingException("DataJud unavailable")

        mockMvc.post("/api/v1/datajud/buscar") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"numeroProcesso":"1234567-89.2023.8.26.0100"}"""
        }.andExpect {
            status { isServiceUnavailable() }
        }
    }
}
