package br.com.precatorios.controller

import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.engine.BfsProspeccaoEngine
import br.com.precatorios.exception.GlobalExceptionHandler
import br.com.precatorios.repository.LeadRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.service.ProspeccaoService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.util.Optional

@WebMvcTest(ProspeccaoController::class)
@Import(GlobalExceptionHandler::class)
class ProspeccaoControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var prospeccaoService: ProspeccaoService

    @MockkBean
    lateinit var bfsProspeccaoEngine: BfsProspeccaoEngine

    @MockkBean
    lateinit var prospeccaoRepository: ProspeccaoRepository

    @MockkBean
    lateinit var leadRepository: LeadRepository

    private val validCnj = "1234567-89.2023.8.26.0100"

    private fun buildProspeccao(
        id: Long = 1L,
        status: StatusProspeccao = StatusProspeccao.EM_ANDAMENTO,
        processosVisitados: Int = 3,
        credoresEncontrados: Int = 5,
        leadsQualificados: Int = 2,
        erroMensagem: String? = null
    ): Prospeccao {
        return Prospeccao().apply {
            this.id = id
            this.processoSemente = validCnj
            this.status = status
            this.profundidadeMax = 2
            this.maxCredores = 50
            this.processosVisitados = processosVisitados
            this.credoresEncontrados = credoresEncontrados
            this.leadsQualificados = leadsQualificados
            this.dataInicio = LocalDateTime.of(2023, 1, 1, 10, 0)
            this.dataFim = if (status == StatusProspeccao.CONCLUIDA) LocalDateTime.of(2023, 1, 1, 10, 30) else null
            this.erroMensagem = erroMensagem
        }
    }

    private fun buildLead(): Lead {
        return Lead().apply {
            this.id = 10L
            this.score = 85
            this.scoreDetalhes = null  // null means objectMapper.readValue is NOT called
            this.statusContato = StatusContato.NAO_CONTACTADO
            this.dataCriacao = LocalDateTime.of(2023, 1, 1, 10, 15)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task 1 tests: POST and GET /{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `POST iniciar with valid CNJ returns 202 with prospeccaoId`() {
        val prospeccao = buildProspeccao()
        every { prospeccaoService.criar(validCnj, null, null) } returns prospeccao
        justRun {
            bfsProspeccaoEngine.start(
                prospeccaoId = 1L,
                processoSemente = validCnj,
                profundidadeMaxima = 2,
                maxCredores = 50,
                entidadesDevedoras = null,
                valorMinimo = null,
                apenasAlimentar = null,
                apenasPendentes = null
            )
        }

        mockMvc.post("/api/v1/prospeccao") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"processoSemente": "$validCnj"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.prospeccaoId") { value(1) }
        }
    }

    @Test
    fun `POST iniciar with invalid CNJ format returns 400`() {
        mockMvc.post("/api/v1/prospeccao") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"processoSemente": "invalid"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST iniciar with missing processoSemente returns 400`() {
        mockMvc.post("/api/v1/prospeccao") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET status for EM_ANDAMENTO returns 200 with counters`() {
        val prospeccao = buildProspeccao(status = StatusProspeccao.EM_ANDAMENTO)
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)

        mockMvc.get("/api/v1/prospeccao/1")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("EM_ANDAMENTO") }
                jsonPath("$.processosVisitados") { value(3) }
                jsonPath("$.credoresEncontrados") { value(5) }
                jsonPath("$.leadsQualificados") { value(2) }
            }
    }

    @Test
    fun `GET status for EM_ANDAMENTO includes Retry-After header with value 10`() {
        val prospeccao = buildProspeccao(status = StatusProspeccao.EM_ANDAMENTO)
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)

        mockMvc.get("/api/v1/prospeccao/1")
            .andExpect {
                status { isOk() }
                header { string("Retry-After", "10") }
            }
    }

    @Test
    fun `GET status for CONCLUIDA returns 200 with leads array`() {
        val prospeccao = buildProspeccao(status = StatusProspeccao.CONCLUIDA)
        val lead = buildLead()
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)
        every { leadRepository.findByProspeccaoId(1L) } returns listOf(lead)

        mockMvc.get("/api/v1/prospeccao/1")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CONCLUIDA") }
                jsonPath("$.leads") { isArray() }
                jsonPath("$.leads[0].score") { value(85) }
            }
    }

    @Test
    fun `GET status for ERRO returns 200 with erroMensagem`() {
        val prospeccao = buildProspeccao(status = StatusProspeccao.ERRO, erroMensagem = "Timeout ao acessar TJ-SP")
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)

        mockMvc.get("/api/v1/prospeccao/1")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ERRO") }
                jsonPath("$.erroMensagem") { value("Timeout ao acessar TJ-SP") }
            }
    }

    @Test
    fun `GET status for non-existent id returns 404`() {
        every { prospeccaoRepository.findById(999L) } returns Optional.empty()

        mockMvc.get("/api/v1/prospeccao/999")
            .andExpect {
                status { isNotFound() }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task 2 tests: GET list (pagination + status filter)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GET listar returns 200 with paginated list`() {
        val prospeccao = buildProspeccao(status = StatusProspeccao.CONCLUIDA)
        every { prospeccaoRepository.findAll(any<Pageable>()) } returns PageImpl(listOf(prospeccao))

        mockMvc.get("/api/v1/prospeccao")
            .andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.content[0].id") { value(1) }
                jsonPath("$.content[0].status") { value("CONCLUIDA") }
            }
    }

    @Test
    fun `GET listar with status filter returns only matching runs`() {
        val prospeccao = buildProspeccao(status = StatusProspeccao.CONCLUIDA)
        every {
            prospeccaoRepository.findByStatus(StatusProspeccao.CONCLUIDA, any<Pageable>())
        } returns PageImpl(listOf(prospeccao))

        mockMvc.get("/api/v1/prospeccao") {
            param("status", "CONCLUIDA")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.content[0].status") { value("CONCLUIDA") }
        }
    }

    @Test
    fun `GET listar with no results returns 200 with empty content`() {
        every { prospeccaoRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())

        mockMvc.get("/api/v1/prospeccao")
            .andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { value(0) }
            }
    }

    @Test
    fun `GET listar with pagination params returns correct page metadata`() {
        val prospeccoes = (1..3).map { buildProspeccao(id = it.toLong()) }
        every { prospeccaoRepository.findAll(any<Pageable>()) } returns PageImpl(prospeccoes)

        mockMvc.get("/api/v1/prospeccao") {
            param("page", "0")
            param("size", "5")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(3) }
            jsonPath("$.totalPages") { value(1) }
        }
    }
}
