package br.com.precatorios.integration

import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.dto.ProspeccaoIniciadaDTO
import br.com.precatorios.dto.ProspeccaoStatusDTO
import br.com.precatorios.repository.LeadRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.scraper.BuscaResultado
import br.com.precatorios.scraper.CacScraper
import br.com.precatorios.scraper.DataJudClient
import br.com.precatorios.scraper.DataJudResult
import br.com.precatorios.scraper.EsajScraper
import br.com.precatorios.scraper.IncidenteScraped
import br.com.precatorios.scraper.ParteScraped
import br.com.precatorios.scraper.PrecatorioScraped
import br.com.precatorios.scraper.ProcessoScraped
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FullStackProspeccaoIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @MockkBean
    lateinit var esajScraper: EsajScraper

    @MockkBean
    lateinit var cacScraper: CacScraper

    @MockkBean
    lateinit var dataJudClient: DataJudClient

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    @Autowired
    lateinit var leadRepository: LeadRepository

    @Autowired
    lateinit var prospeccaoRepository: ProspeccaoRepository

    private val seedProcesso = "1111111-11.2024.8.26.0100"
    private val precatorioNumero = "0001234-11.2024.8.26.0000"

    @BeforeEach
    fun setupMocks() {
        // DataJud: relaxed mock returning empty results (health check + any BFS calls)
        every { dataJudClient.doBuscarPorNumero(any()) } returns DataJudResult(hits = emptyList(), total = 0)
        every { dataJudClient.buscarPorNumeroProcesso(any()) } returns DataJudResult(hits = emptyList(), total = 0)
        every { dataJudClient.buscarPorMunicipio(any()) } returns DataJudResult(hits = emptyList(), total = 0)

        // EsajScraper: seed process returns 2 parties and 1 precatorio incident
        every { esajScraper.fetchProcesso(seedProcesso) } returns ProcessoScraped(
            numero = seedProcesso,
            classe = "Execucao",
            assunto = null,
            foro = null,
            vara = null,
            juiz = null,
            valorAcao = null,
            partes = listOf(
                ParteScraped("Maria Oliveira", "Exequente", "Dr. Paulo"),
                ParteScraped("Jose Santos", "Credor", "Dr. Ana")
            ),
            incidentes = listOf(
                IncidenteScraped(precatorioNumero, "Precatorio", null)
            ),
            missingFields = emptyList(),
            rawHtml = """{"html":"<html>mock</html>"}"""
        )

        // EsajScraper: buscarPorNome returns empty list (D-06: no BFS expansion beyond seed)
        every { esajScraper.buscarPorNome(any()) } returns emptyList<BuscaResultado>()

        // CacScraper: precatorio mock with high score values
        every { cacScraper.fetchPrecatorio(precatorioNumero) } returns PrecatorioScraped(
            numeroPrecatorio = precatorioNumero,
            numeroProcesso = seedProcesso,
            entidadeDevedora = "FAZENDA DO ESTADO DE SAO PAULO",
            valorOriginal = "200000.00",
            valorAtualizado = "250000.00",
            natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE",
            posicaoCronologica = 50,
            dataExpedicao = "2024-01-15",
            missingFields = emptyList(),
            rawHtml = """{"html":"<html>prec-mock</html>"}"""
        )
    }

    @Test
    fun `full BFS pipeline - POST prospeccao, wait for CONCLUIDA, assert scored leads`() {
        // 1. POST /api/v1/prospeccao
        val requestBody = mapOf(
            "processoSemente" to seedProcesso,
            "profundidadeMaxima" to 1,
            "maxCredores" to 50
        )
        val postResponse = testRestTemplate.postForEntity(
            "/api/v1/prospeccao",
            requestBody,
            ProspeccaoIniciadaDTO::class.java
        )

        assertThat(postResponse.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        val prospeccaoId = postResponse.body!!.prospeccaoId

        // 2. Await CONCLUIDA status via polling GET /api/v1/prospeccao/{id}
        await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until {
                val statusResponse = testRestTemplate.getForEntity(
                    "/api/v1/prospeccao/$prospeccaoId",
                    ProspeccaoStatusDTO::class.java
                )
                statusResponse.body?.status == StatusProspeccao.CONCLUIDA.name
            }

        // 3. Verify final prospeccao state
        val finalStatus = testRestTemplate.getForEntity(
            "/api/v1/prospeccao/$prospeccaoId",
            ProspeccaoStatusDTO::class.java
        ).body!!

        assertThat(finalStatus.processosVisitados).isGreaterThanOrEqualTo(1)
        assertThat(finalStatus.credoresEncontrados).isGreaterThanOrEqualTo(1)

        // 4. Verify leads in database
        val leads = leadRepository.findAll()
        assertThat(leads).isNotEmpty
        val scoredLead = leads.first { it.score > 0 }
        assertThat(scoredLead.score).isGreaterThan(0)
        assertThat(scoredLead.statusContato.name).isEqualTo("NAO_CONTACTADO")
        assertThat(scoredLead.credor).isNotNull
        assertThat(scoredLead.credor!!.nome).isIn("Maria Oliveira", "Jose Santos")

        // 5. GET /api/v1/leads (plan 05-01 endpoint) returns HTTP 200 with leads
        val leadsResponse = testRestTemplate.getForEntity(
            "/api/v1/leads",
            Map::class.java
        )
        assertThat(leadsResponse.statusCode).isEqualTo(HttpStatus.OK)
        val content = leadsResponse.body!!["content"] as List<*>
        assertThat(content).isNotEmpty
    }
}
