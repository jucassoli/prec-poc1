package br.com.precatorios.engine

import br.com.precatorios.domain.Credor
import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.Precatorio
import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.LeadRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.service.ScoredResult
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class ProspeccaoLeadPersistenceHelperTest {

    private lateinit var leadRepository: LeadRepository
    private lateinit var prospeccaoRepository: ProspeccaoRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var helper: ProspeccaoLeadPersistenceHelper

    @BeforeEach
    fun setup() {
        leadRepository = mockk()
        prospeccaoRepository = mockk()
        objectMapper = ObjectMapper()
        helper = ProspeccaoLeadPersistenceHelper(leadRepository, prospeccaoRepository, objectMapper)
    }

    // Test 1: persistirLead creates a Lead with correct score, scoreDetalhes, prospeccao/credor/precatorio links, and dataCriacao set
    @Test
    fun `persistirLead creates Lead with correct fields`() {
        val prospeccao = Prospeccao().apply { id = 1L }
        val credor = Credor().apply { id = 2L }
        val precatorio = Precatorio().apply { id = 3L }
        val scored = ScoredResult(total = 75, detalhes = mapOf("valor" to 30, "natureza" to 10, "total" to 40))

        val savedLeadSlot = slot<Lead>()
        every { leadRepository.save(capture(savedLeadSlot)) } answers { savedLeadSlot.captured.also { it.id = 10L } }

        val result = helper.persistirLead(prospeccao, credor, precatorio, scored)

        assertThat(result.score).isEqualTo(75)
        assertThat(result.scoreDetalhes).contains("valor")
        assertThat(result.statusContato).isEqualTo(StatusContato.NAO_CONTACTADO)
        assertThat(result.dataCriacao).isNotNull
        assertThat(result.prospeccao).isSameAs(prospeccao)
        assertThat(result.credor).isSameAs(credor)
        assertThat(result.precatorio).isSameAs(precatorio)

        verify { leadRepository.save(any()) }
    }

    // Test 2: atualizarContadores updates processosVisitados, credoresEncontrados, leadsQualificados on the Prospeccao entity
    @Test
    fun `atualizarContadores updates all three counters on Prospeccao`() {
        val prospeccao = Prospeccao().apply {
            id = 1L
            processosVisitados = 0
            credoresEncontrados = 0
            leadsQualificados = 0
        }
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)
        every { prospeccaoRepository.save(any()) } answers { firstArg() }

        helper.atualizarContadores(1L, 5, 3, 2)

        verify {
            prospeccaoRepository.save(withArg { p ->
                assertThat(p.processosVisitados).isEqualTo(5)
                assertThat(p.credoresEncontrados).isEqualTo(3)
                assertThat(p.leadsQualificados).isEqualTo(2)
            })
        }
    }

    // Test 3: finalizarProspeccao sets status to CONCLUIDA, dataFim, erroMensagem if errors, and counters
    @Test
    fun `finalizarProspeccao sets CONCLUIDA status with dataFim and erroMensagem`() {
        val prospeccao = Prospeccao().apply { id = 1L }
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)
        every { prospeccaoRepository.save(any()) } answers { firstArg() }

        helper.finalizarProspeccao(1L, 10, 5, 3, "some error", StatusProspeccao.CONCLUIDA)

        verify {
            prospeccaoRepository.save(withArg { p ->
                assertThat(p.status).isEqualTo(StatusProspeccao.CONCLUIDA)
                assertThat(p.dataFim).isNotNull
                assertThat(p.erroMensagem).isEqualTo("some error")
                assertThat(p.processosVisitados).isEqualTo(10)
                assertThat(p.credoresEncontrados).isEqualTo(5)
                assertThat(p.leadsQualificados).isEqualTo(3)
            })
        }
    }

    @Test
    fun `finalizarProspeccao with null erroMensagem sets null on entity`() {
        val prospeccao = Prospeccao().apply { id = 1L }
        every { prospeccaoRepository.findById(1L) } returns Optional.of(prospeccao)
        every { prospeccaoRepository.save(any()) } answers { firstArg() }

        helper.finalizarProspeccao(1L, 2, 1, 1, null, StatusProspeccao.CONCLUIDA)

        verify {
            prospeccaoRepository.save(withArg { p ->
                assertThat(p.erroMensagem).isNull()
            })
        }
    }
}
