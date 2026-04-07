package br.com.precatorios.service

import br.com.precatorios.domain.Credor
import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.Precatorio
import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.dto.AtualizarStatusContatoRequestDTO
import br.com.precatorios.dto.LeadResponseDTO
import br.com.precatorios.exception.LeadNaoEncontradoException
import br.com.precatorios.repository.LeadRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

class LeadServiceTest {

    private val leadRepository: LeadRepository = mockk()
    private val objectMapper = ObjectMapper()
    private val leadService = LeadService(leadRepository, objectMapper)

    private fun buildLead(
        id: Long = 1L,
        score: Int = 80,
        statusContato: StatusContato = StatusContato.NAO_CONTACTADO,
        observacao: String? = null
    ): Lead {
        val credor = Credor().apply {
            this.id = 10L
            this.nome = "Joao da Silva"
        }
        val precatorio = Precatorio().apply {
            this.id = 20L
            this.numeroPrecatorio = "PREC-001"
            this.entidadeDevedora = "Estado de SP"
            this.valorAtualizado = BigDecimal("150000.00")
            this.statusPagamento = "PENDENTE"
        }
        return Lead().apply {
            this.id = id
            this.score = score
            this.statusContato = statusContato
            this.observacao = observacao
            this.dataCriacao = LocalDateTime.of(2024, 1, 15, 10, 0)
            this.credor = credor
            this.precatorio = precatorio
        }
    }

    @Test
    fun `listarLeads with scoreMinimo filters leads with score above threshold`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "score"))
        val leads = listOf(buildLead(score = 80), buildLead(id = 2L, score = 70))
        val page = PageImpl(leads, pageable, 2)

        every { leadRepository.findLeadsFiltered(60, null, null, pageable) } returns page

        val result = leadService.listarLeads(
            scoreMinimo = 60,
            statusContato = null,
            entidadeDevedora = null,
            incluirZero = false,
            pageable = pageable
        )

        assertEquals(2, result.totalElements)
        verify { leadRepository.findLeadsFiltered(60, null, null, pageable) }
    }

    @Test
    fun `listarLeads with incluirZero false sets effective scoreMinimo to max of given value and 1`() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(emptyList<Lead>(), pageable, 0)

        // When scoreMinimo is null and incluirZero=false, effectiveScoreMinimo should be 1
        every { leadRepository.findLeadsFiltered(1, null, null, pageable) } returns page

        leadService.listarLeads(
            scoreMinimo = null,
            statusContato = null,
            entidadeDevedora = null,
            incluirZero = false,
            pageable = pageable
        )

        verify { leadRepository.findLeadsFiltered(1, null, null, pageable) }
    }

    @Test
    fun `listarLeads with incluirZero true and no scoreMinimo passes null scoreMinimo`() {
        val pageable = PageRequest.of(0, 20)
        val leads = listOf(buildLead(score = 0), buildLead(id = 2L, score = 50))
        val page = PageImpl(leads, pageable, 2)

        every { leadRepository.findLeadsFiltered(null, null, null, pageable) } returns page

        val result = leadService.listarLeads(
            scoreMinimo = null,
            statusContato = null,
            entidadeDevedora = null,
            incluirZero = true,
            pageable = pageable
        )

        assertEquals(2, result.totalElements)
        verify { leadRepository.findLeadsFiltered(null, null, null, pageable) }
    }

    @Test
    fun `atualizarStatusContato updates statusContato and observacao on existing lead`() {
        val lead = buildLead()
        val savedLead = buildLead(statusContato = StatusContato.CONTACTADO, observacao = "Ligacao feita")
        val request = AtualizarStatusContatoRequestDTO(
            statusContato = StatusContato.CONTACTADO,
            observacao = "Ligacao feita"
        )

        every { leadRepository.findById(1L) } returns Optional.of(lead)
        every { leadRepository.save(any()) } returns savedLead

        val result = leadService.atualizarStatusContato(1L, request)

        assertEquals(StatusContato.CONTACTADO.name, result.statusContato)
        assertEquals("Ligacao feita", result.observacao)
        verify { leadRepository.save(any()) }
    }

    @Test
    fun `atualizarStatusContato on non-existent lead throws LeadNaoEncontradoException`() {
        every { leadRepository.findById(999L) } returns Optional.empty()

        assertThrows<LeadNaoEncontradoException> {
            leadService.atualizarStatusContato(
                999L,
                AtualizarStatusContatoRequestDTO(statusContato = StatusContato.CONTACTADO)
            )
        }
    }

    @Test
    fun `LeadResponseDTO fromEntity correctly maps nested credor and precatorio fields`() {
        val lead = buildLead()

        val dto = LeadResponseDTO.fromEntity(lead, objectMapper)

        assertEquals(1L, dto.id)
        assertEquals(80, dto.score)
        assertEquals(StatusContato.NAO_CONTACTADO.name, dto.statusContato)
        assertNull(dto.observacao)
        assertNotNull(dto.credor)
        assertEquals(10L, dto.credor.id)
        assertEquals("Joao da Silva", dto.credor.nome)
        assertNotNull(dto.precatorio)
        assertEquals(20L, dto.precatorio.id)
        assertEquals("PREC-001", dto.precatorio.numero)
        assertEquals("Estado de SP", dto.precatorio.entidadeDevedora)
        assertEquals(BigDecimal("150000.00"), dto.precatorio.valorAtualizado)
        assertEquals("PENDENTE", dto.precatorio.statusPagamento)
    }
}
