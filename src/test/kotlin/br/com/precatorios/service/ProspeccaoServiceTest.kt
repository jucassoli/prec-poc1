package br.com.precatorios.service

import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.ProspeccaoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProspeccaoServiceTest {

    private lateinit var prospeccaoRepository: ProspeccaoRepository
    private lateinit var service: ProspeccaoService

    @BeforeEach
    fun setup() {
        prospeccaoRepository = mockk()
        service = ProspeccaoService(prospeccaoRepository)
    }

    // Test 4: ProspeccaoService.criar persists a new Prospeccao with processoSemente, profundidadeMax, maxCredores, status=EM_ANDAMENTO, dataInicio set
    @Test
    fun `criar persists Prospeccao with all required fields`() {
        val savedSlot = slot<Prospeccao>()
        every { prospeccaoRepository.save(capture(savedSlot)) } answers {
            savedSlot.captured.also { it.id = 42L }
        }

        val result = service.criar("1234567-89.2023.8.26.0100", 3, 100)

        assertThat(result.id).isEqualTo(42L)
        assertThat(result.processoSemente).isEqualTo("1234567-89.2023.8.26.0100")
        assertThat(result.profundidadeMax).isEqualTo(3)
        assertThat(result.maxCredores).isEqualTo(100)
        assertThat(result.status).isEqualTo(StatusProspeccao.EM_ANDAMENTO)
        assertThat(result.dataInicio).isNotNull

        verify { prospeccaoRepository.save(any()) }
    }

    @Test
    fun `criar uses default values when profundidadeMaxima and maxCredores are null`() {
        val savedSlot = slot<Prospeccao>()
        every { prospeccaoRepository.save(capture(savedSlot)) } answers {
            savedSlot.captured.also { it.id = 1L }
        }

        val result = service.criar("1234567-89.2023.8.26.0100", null, null)

        assertThat(result.profundidadeMax).isEqualTo(2)
        assertThat(result.maxCredores).isEqualTo(50)
    }
}
