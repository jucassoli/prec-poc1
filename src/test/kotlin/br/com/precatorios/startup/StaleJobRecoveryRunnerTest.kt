package br.com.precatorios.startup

import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.ProspeccaoRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

class StaleJobRecoveryRunnerTest {

    private val prospeccaoRepository = mockk<ProspeccaoRepository>(relaxed = true)
    private val runner = StaleJobRecoveryRunner(prospeccaoRepository)

    @Test
    fun `recovers stale EM_ANDAMENTO jobs setting ERRO with recovery message`() {
        val dataInicio1 = LocalDateTime.of(2026, 4, 5, 10, 30, 0)
        val dataInicio2 = LocalDateTime.of(2026, 4, 5, 14, 15, 0)
        val p1 = Prospeccao().apply {
            id = 1L; processoSemente = "1111111-11.2024.8.26.0100"
            status = StatusProspeccao.EM_ANDAMENTO; dataInicio = dataInicio1
        }
        val p2 = Prospeccao().apply {
            id = 2L; processoSemente = "2222222-22.2024.8.26.0100"
            status = StatusProspeccao.EM_ANDAMENTO; dataInicio = dataInicio2
        }
        every { prospeccaoRepository.findByStatus(StatusProspeccao.EM_ANDAMENTO, Pageable.unpaged()) } returns
            PageImpl(listOf(p1, p2))
        every { prospeccaoRepository.save(any()) } answers { firstArg() }

        runner.run(DefaultApplicationArguments())

        assertThat(p1.status).isEqualTo(StatusProspeccao.ERRO)
        assertThat(p1.erroMensagem).isEqualTo("Interrompida por reinicio (iniciada em $dataInicio1)")
        assertThat(p1.dataFim).isNotNull()
        assertThat(p2.status).isEqualTo(StatusProspeccao.ERRO)
        assertThat(p2.erroMensagem).isEqualTo("Interrompida por reinicio (iniciada em $dataInicio2)")
        verify(exactly = 2) { prospeccaoRepository.save(any()) }
    }

    @Test
    fun `no-op when no stale jobs exist`() {
        every { prospeccaoRepository.findByStatus(StatusProspeccao.EM_ANDAMENTO, Pageable.unpaged()) } returns
            PageImpl(emptyList())

        runner.run(DefaultApplicationArguments())

        verify(exactly = 0) { prospeccaoRepository.save(any()) }
    }
}
