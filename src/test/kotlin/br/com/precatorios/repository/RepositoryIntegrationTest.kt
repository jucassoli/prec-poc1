package br.com.precatorios.repository

import br.com.precatorios.domain.Credor
import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.Precatorio
import br.com.precatorios.domain.Processo
import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusContato
import br.com.precatorios.domain.enums.StatusProspeccao
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@Testcontainers
@Transactional
class RepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    lateinit var processoRepository: ProcessoRepository

    @Autowired
    lateinit var credorRepository: CredorRepository

    @Autowired
    lateinit var precatorioRepository: PrecatorioRepository

    @Autowired
    lateinit var prospeccaoRepository: ProspeccaoRepository

    @Autowired
    lateinit var leadRepository: LeadRepository

    private fun createTestProcesso(numero: String = "1234567-89.2024.8.26.0100"): Processo {
        val processo = Processo()
        processo.numero = numero
        processo.classe = "Execução de Título Extrajudicial"
        processo.status = "PENDENTE"
        processo.dataColeta = LocalDateTime.now()
        return processoRepository.save(processo)
    }

    @Test
    fun `PERS-01 - saves and retrieves Processo by numero`() {
        val processo = createTestProcesso()

        val found = processoRepository.findByNumero(processo.numero)
        assertThat(found).isNotNull
        assertThat(found!!.numero).isEqualTo(processo.numero)
        assertThat(processoRepository.existsByNumero(processo.numero)).isTrue
    }

    @Test
    fun `PERS-01 - existsByNumero returns false for unknown numero`() {
        assertThat(processoRepository.existsByNumero("9999999-99.9999.9.99.9999")).isFalse
    }

    @Test
    fun `PERS-02 - enforces unique constraint on (nome, processo_id)`() {
        val processo = createTestProcesso()

        val credor1 = Credor()
        credor1.nome = "João da Silva"
        credor1.processo = processo
        credor1.dataDescoberta = LocalDateTime.now()
        credorRepository.save(credor1)
        credorRepository.flush()

        assertThat(credorRepository.existsByNomeAndProcessoId("João da Silva", processo.id!!)).isTrue

        val credor2 = Credor()
        credor2.nome = "Maria Oliveira"
        credor2.processo = processo
        credor2.dataDescoberta = LocalDateTime.now()
        credorRepository.save(credor2)

        val credores = credorRepository.findByProcessoId(processo.id!!)
        assertThat(credores).hasSize(2)
    }

    @Test
    fun `PERS-03 - saves and retrieves Precatorio linked to Credor`() {
        val processo = createTestProcesso()

        val credor = Credor()
        credor.nome = "Ana Santos"
        credor.processo = processo
        credor.dataDescoberta = LocalDateTime.now()
        val savedCredor = credorRepository.save(credor)

        val precatorio = Precatorio()
        precatorio.numeroPrecatorio = "0001234-11.2024.8.26.0000"
        precatorio.numeroProcesso = processo.numero
        precatorio.entidadeDevedora = "Estado de São Paulo"
        precatorio.valorOriginal = BigDecimal("250000.00")
        precatorio.credor = savedCredor
        precatorio.dataColeta = LocalDateTime.now()
        val savedPrecatorio = precatorioRepository.save(precatorio)

        val found = precatorioRepository.findByCredorId(savedCredor.id!!)
        assertThat(found).hasSize(1)
        assertThat(found[0].id).isEqualTo(savedPrecatorio.id)
        assertThat(found[0].numeroPrecatorio).isEqualTo("0001234-11.2024.8.26.0000")
    }

    @Test
    fun `PERS-04 - saves Prospeccao with status and retrieves as enum`() {
        val prospeccao = Prospeccao()
        prospeccao.processoSemente = "1111111-11.2024.8.26.0100"
        prospeccao.status = StatusProspeccao.EM_ANDAMENTO
        prospeccao.profundidadeMax = 2
        prospeccao.maxCredores = 50
        prospeccao.dataInicio = LocalDateTime.now()
        val saved = prospeccaoRepository.save(prospeccao)
        prospeccaoRepository.flush()

        val found = prospeccaoRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.status).isEqualTo(StatusProspeccao.EM_ANDAMENTO)
        assertThat(found.processoSemente).isEqualTo("1111111-11.2024.8.26.0100")
    }

    @Test
    fun `PERS-05 - saves Lead linking Prospeccao, Credor, and Precatorio`() {
        val processo = createTestProcesso("2222222-22.2024.8.26.0100")

        val credor = Credor()
        credor.nome = "Pedro Lima"
        credor.processo = processo
        credor.dataDescoberta = LocalDateTime.now()
        val savedCredor = credorRepository.save(credor)

        val precatorio = Precatorio()
        precatorio.numeroProcesso = processo.numero
        precatorio.credor = savedCredor
        precatorio.dataColeta = LocalDateTime.now()
        val savedPrecatorio = precatorioRepository.save(precatorio)

        val prospeccao = Prospeccao()
        prospeccao.processoSemente = processo.numero
        prospeccao.status = StatusProspeccao.CONCLUIDA
        prospeccao.dataInicio = LocalDateTime.now()
        val savedProspeccao = prospeccaoRepository.save(prospeccao)

        val lead = Lead()
        lead.prospeccao = savedProspeccao
        lead.credor = savedCredor
        lead.precatorio = savedPrecatorio
        lead.score = 75
        lead.statusContato = StatusContato.NAO_CONTACTADO
        lead.dataCriacao = LocalDateTime.now()
        val savedLead = leadRepository.save(lead)

        val leads = leadRepository.findByProspeccaoId(savedProspeccao.id!!)
        assertThat(leads).hasSize(1)
        assertThat(leads[0].id).isEqualTo(savedLead.id)
        assertThat(leads[0].score).isEqualTo(75)
    }

    @Test
    fun `PERS-06 - JSONB round-trips for Processo dadosBrutos`() {
        val jsonPayload = """{"html": "<div>test</div>", "status": 200}"""

        val processo = Processo()
        processo.numero = "3333333-33.2024.8.26.0100"
        processo.dadosBrutos = jsonPayload
        processo.dataColeta = LocalDateTime.now()
        val saved = processoRepository.save(processo)
        processoRepository.flush()

        val found = processoRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.dadosBrutos).isNotNull
        assertThat(found.dadosBrutos).contains("html")
        assertThat(found.dadosBrutos).contains("<div>test</div>")
    }

    @Test
    fun `PERS-06 - JSONB round-trips for Precatorio dadosBrutos`() {
        val processo = createTestProcesso("4444444-44.2024.8.26.0100")

        val credor = Credor()
        credor.nome = "Carlos Ferreira"
        credor.processo = processo
        credor.dataDescoberta = LocalDateTime.now()
        val savedCredor = credorRepository.save(credor)

        val jsonPayload = """{"precatorio": "0001", "valor": "100000"}"""

        val precatorio = Precatorio()
        precatorio.credor = savedCredor
        precatorio.dadosBrutos = jsonPayload
        precatorio.dataColeta = LocalDateTime.now()
        val saved = precatorioRepository.save(precatorio)
        precatorioRepository.flush()

        val found = precatorioRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.dadosBrutos).isNotNull
        assertThat(found.dadosBrutos).contains("precatorio")
        assertThat(found.dadosBrutos).contains("100000")
    }

    @Test
    fun `PERS-06 - JSONB round-trips for Lead scoreDetalhes`() {
        val processo = createTestProcesso("5555555-55.2024.8.26.0100")

        val credor = Credor()
        credor.nome = "Fernanda Costa"
        credor.processo = processo
        credor.dataDescoberta = LocalDateTime.now()
        val savedCredor = credorRepository.save(credor)

        val prospeccao = Prospeccao()
        prospeccao.processoSemente = processo.numero
        prospeccao.status = StatusProspeccao.EM_ANDAMENTO
        prospeccao.dataInicio = LocalDateTime.now()
        val savedProspeccao = prospeccaoRepository.save(prospeccao)

        val jsonPayload = """{"valor": 50, "natureza": 30, "status": 20}"""

        val lead = Lead()
        lead.prospeccao = savedProspeccao
        lead.credor = savedCredor
        lead.score = 100
        lead.scoreDetalhes = jsonPayload
        lead.dataCriacao = LocalDateTime.now()
        val saved = leadRepository.save(lead)
        leadRepository.flush()

        val found = leadRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.scoreDetalhes).isNotNull
        assertThat(found.scoreDetalhes).contains("valor")
        assertThat(found.scoreDetalhes).contains("natureza")
    }
}
