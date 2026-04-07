package br.com.precatorios.controller

import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.dto.AtualizarStatusContatoRequestDTO
import br.com.precatorios.dto.LeadResponseDTO
import br.com.precatorios.exception.GlobalExceptionHandler
import br.com.precatorios.exception.LeadNaoEncontradoException
import br.com.precatorios.service.LeadService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(LeadController::class)
@Import(GlobalExceptionHandler::class)
class LeadControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var leadService: LeadService

    private fun buildLeadResponseDTO(
        id: Long = 1L,
        score: Int = 80,
        statusContato: StatusContato = StatusContato.NAO_CONTACTADO,
        observacao: String? = null
    ) = LeadResponseDTO(
        id = id,
        score = score,
        scoreDetalhes = null,
        statusContato = statusContato.name,
        observacao = observacao,
        dataCriacao = LocalDateTime.of(2024, 1, 15, 10, 0),
        credor = LeadResponseDTO.CredorSummaryDTO(id = 10L, nome = "Joao da Silva"),
        precatorio = LeadResponseDTO.PrecatorioSummaryDTO(
            id = 20L,
            numero = "PREC-001",
            valorAtualizado = BigDecimal("150000.00"),
            entidadeDevedora = "Estado de SP",
            statusPagamento = "PENDENTE"
        )
    )

    @Test
    fun `GET leads returns 200 with paginated LeadResponseDTO list sorted score DESC by default`() {
        val leads = listOf(buildLeadResponseDTO(score = 80), buildLeadResponseDTO(id = 2L, score = 60))
        val page = PageImpl(leads)

        every { leadService.listarLeads(null, null, null, false, any()) } returns page

        mockMvc.get("/api/v1/leads")
            .andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.content[0].score") { value(80) }
                jsonPath("$.content[0].credor.nome") { value("Joao da Silva") }
                jsonPath("$.content[0].precatorio.entidadeDevedora") { value("Estado de SP") }
            }
    }

    @Test
    fun `GET leads with scoreMinimo and statusContato filters returns filtered results`() {
        val leads = listOf(buildLeadResponseDTO(score = 75))
        val page = PageImpl(leads)

        every { leadService.listarLeads(60, StatusContato.NAO_CONTACTADO, null, false, any()) } returns page

        mockMvc.get("/api/v1/leads") {
            param("scoreMinimo", "60")
            param("statusContato", "NAO_CONTACTADO")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.content[0].statusContato") { value("NAO_CONTACTADO") }
        }
    }

    @Test
    fun `GET leads with no params excludes zero-score leads by default`() {
        val leads = listOf(buildLeadResponseDTO(score = 50))
        val page = PageImpl(leads)

        every { leadService.listarLeads(null, null, null, false, any()) } returns page

        mockMvc.get("/api/v1/leads")
            .andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
            }

        // Verify service was called with incluirZero=false
        io.mockk.verify { leadService.listarLeads(null, null, null, false, any()) }
    }

    @Test
    fun `GET leads with incluirZero=true passes flag to service`() {
        val leads = listOf(buildLeadResponseDTO(score = 0), buildLeadResponseDTO(id = 2L, score = 50))
        val page = PageImpl(leads)

        every { leadService.listarLeads(null, null, null, true, any()) } returns page

        mockMvc.get("/api/v1/leads") {
            param("incluirZero", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(2) }
        }
    }

    @Test
    fun `GET leads with sort param overrides default sort`() {
        val leads = listOf(buildLeadResponseDTO())
        val page = PageImpl(leads)

        every { leadService.listarLeads(null, null, null, false, any()) } returns page

        mockMvc.get("/api/v1/leads") {
            param("sort", "dataCriacao,desc")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
        }
    }

    @Test
    fun `PATCH leads id status with valid body returns 200 with updated lead`() {
        val updated = buildLeadResponseDTO(statusContato = StatusContato.CONTACTADO, observacao = "Ligacao feita")
        val requestBody = AtualizarStatusContatoRequestDTO(
            statusContato = StatusContato.CONTACTADO,
            observacao = "Ligacao feita"
        )

        every { leadService.atualizarStatusContato(1L, any()) } returns updated

        mockMvc.patch("/api/v1/leads/1/status") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.statusContato") { value("CONTACTADO") }
            jsonPath("$.observacao") { value("Ligacao feita") }
        }
    }

    @Test
    fun `PATCH leads non-existent id returns 404 with structured error`() {
        val requestBody = AtualizarStatusContatoRequestDTO(statusContato = StatusContato.CONTACTADO)

        every { leadService.atualizarStatusContato(999L, any()) } throws
            LeadNaoEncontradoException("Lead id=999 nao encontrado")

        mockMvc.patch("/api/v1/leads/999/status") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
            jsonPath("$.message") { value("Lead id=999 nao encontrado") }
        }
    }

    @Test
    fun `PATCH leads id status with missing statusContato returns 400`() {
        val invalidBody = """{}"""

        mockMvc.patch("/api/v1/leads/1/status") {
            contentType = MediaType.APPLICATION_JSON
            content = invalidBody
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
