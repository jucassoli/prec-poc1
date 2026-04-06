package br.com.precatorios.engine

import br.com.precatorios.domain.Credor
import br.com.precatorios.domain.Lead
import br.com.precatorios.domain.Precatorio
import br.com.precatorios.domain.Processo
import br.com.precatorios.domain.Prospeccao
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.repository.CredorRepository
import br.com.precatorios.repository.PrecatorioRepository
import br.com.precatorios.repository.ProcessoRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.scraper.BuscaResultado
import br.com.precatorios.scraper.CacScraper
import br.com.precatorios.scraper.EsajScraper
import br.com.precatorios.scraper.IncidenteScraped
import br.com.precatorios.scraper.ParteScraped
import br.com.precatorios.scraper.PrecatorioScraped
import br.com.precatorios.scraper.ProcessoScraped
import br.com.precatorios.service.ScoredResult
import br.com.precatorios.service.ScoringService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Async
import java.util.Optional

class BfsProspeccaoEngineTest {

    private lateinit var esajScraper: EsajScraper
    private lateinit var cacScraper: CacScraper
    private lateinit var scoringService: ScoringService
    private lateinit var persistenceHelper: ProspeccaoLeadPersistenceHelper
    private lateinit var prospeccaoRepository: ProspeccaoRepository
    private lateinit var processoRepository: ProcessoRepository
    private lateinit var credorRepository: CredorRepository
    private lateinit var precatorioRepository: PrecatorioRepository
    private lateinit var engine: BfsProspeccaoEngine

    private val SEED = "1234567-89.2023.8.26.0100"
    private val PROSPECCAO_ID = 1L

    @BeforeEach
    fun setup() {
        esajScraper = mockk()
        cacScraper = mockk()
        scoringService = mockk()
        persistenceHelper = mockk()
        prospeccaoRepository = mockk()
        processoRepository = mockk()
        credorRepository = mockk()
        precatorioRepository = mockk()

        engine = BfsProspeccaoEngine(
            esajScraper = esajScraper,
            cacScraper = cacScraper,
            scoringService = scoringService,
            persistenceHelper = persistenceHelper,
            prospeccaoRepository = prospeccaoRepository,
            processoRepository = processoRepository,
            credorRepository = credorRepository,
            precatorioRepository = precatorioRepository,
            maxSearchResults = 10
        )
    }

    private fun makeProspeccao(id: Long = PROSPECCAO_ID) = Prospeccao().apply {
        this.id = id
        this.processoSemente = SEED
        this.profundidadeMax = 2
        this.maxCredores = 50
    }

    private fun makeProcessoScraped(
        numero: String = SEED,
        partes: List<ParteScraped> = emptyList(),
        incidentes: List<IncidenteScraped> = emptyList()
    ) = ProcessoScraped(
        numero = numero,
        classe = "Execução de Título Extrajudicial",
        assunto = "Precatório",
        foro = "Foro Central",
        vara = "1a Vara",
        juiz = "Dr. Silva",
        valorAcao = "R$ 100.000",
        partes = partes,
        incidentes = incidentes,
        missingFields = emptyList(),
        rawHtml = "<html>test</html>"
    )

    private fun makePrecatorioScraped(numero: String = "PREC-001") = PrecatorioScraped(
        numeroPrecatorio = numero,
        numeroProcesso = SEED,
        entidadeDevedora = "FAZENDA DO ESTADO",
        valorOriginal = "100000.00",
        valorAtualizado = "120000.00",
        natureza = "ALIMENTAR",
        statusPagamento = "PENDENTE",
        posicaoCronologica = 50,
        dataExpedicao = "2023-01-01",
        missingFields = emptyList(),
        rawHtml = "<html>prec</html>"
    )

    private fun makeProcesso(numero: String = SEED, id: Long = 10L) = Processo().apply {
        this.id = id
        this.numero = numero
    }

    private fun makeCredor(nome: String = "Alice", id: Long = 20L) = Credor().apply {
        this.id = id
        this.nome = nome
    }

    private fun makePrecatorio(id: Long = 30L) = Precatorio().apply {
        this.id = id
    }

    // Test 1 (PROS-01): BFS visits seed process, extracts 2 creditors with precatorio incidents, creates 2 leads
    @Test
    fun `BFS visits seed process and creates leads for each creditor with precatorio`() {
        val prospeccao = makeProspeccao()
        val parte1 = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val parte2 = ParteScraped(nome = "Bob", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-001", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-002", descricao = "Precatório", link = null)

        val scraped = makeProcessoScraped(
            partes = listOf(parte1, parte2),
            incidentes = listOf(incidente1, incidente2)
        )
        val processo = makeProcesso()
        val credor1 = makeCredor("Alice", 20L)
        val credor2 = makeCredor("Bob", 21L)
        val prec1 = makePrecatorio(30L)
        val prec2 = makePrecatorio(31L)
        val scored = ScoredResult(total = 75, detalhes = mapOf("total" to 75))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns scraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" }) } returns credor1
        every { credorRepository.findByNomeAndProcessoId("Bob", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Bob" }) } returns credor2
        every { cacScraper.fetchPrecatorio("PREC-001") } returns makePrecatorioScraped("PREC-001")
        every { cacScraper.fetchPrecatorio("PREC-002") } returns makePrecatorioScraped("PREC-002")
        every { precatorioRepository.save(any()) } returnsMany listOf(prec1, prec2)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(PROSPECCAO_ID, SEED, 0, 50, null, null, null, null)

        verify { esajScraper.fetchProcesso(SEED) }
        verify { cacScraper.fetchPrecatorio("PREC-001") }
        verify { cacScraper.fetchPrecatorio("PREC-002") }
        verify { scoringService.score(any()) }
        // 2 parties × 2 incidentes = 4 lead persists (each party linked to each precatorio incident)
        verify(atLeast = 2) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }

    // Test 2 (PROS-04): Visited set prevents re-scraping the same process
    @Test
    fun `visited set prevents re-scraping same process number`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val scraped = makeProcessoScraped(partes = listOf(parte), incidentes = emptyList())
        val processo = makeProcesso()

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns scraped
        every { processoRepository.findByNumero(SEED) } returns processo
        every { credorRepository.findByNomeAndProcessoId(any(), any()) } returns makeCredor()
        // buscarPorNome returns the seed itself — should NOT be re-scraped
        every { esajScraper.buscarPorNome("Alice") } returns listOf(
            BuscaResultado(numero = SEED, classe = null, assunto = null, foro = null)
        )
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit

        engine.start(PROSPECCAO_ID, SEED, 2, 50, null, null, null, null)

        // fetchProcesso should be called only once for seed, not again for the same seed returned by buscarPorNome
        verify(exactly = 1) { esajScraper.fetchProcesso(SEED) }
    }

    // Test 3 (PROS-06): BFS engine start method has @Async("prospeccaoExecutor") annotation
    @Test
    fun `BFS start method is annotated with @Async prospeccaoExecutor`() {
        val startMethod = BfsProspeccaoEngine::class.java.getMethod(
            "start", Long::class.java, String::class.java, Int::class.java, Int::class.java,
            List::class.java, java.math.BigDecimal::class.java, Boolean::class.javaObjectType, Boolean::class.javaObjectType
        )
        val asyncAnnotation = startMethod.getAnnotation(Async::class.java)
        assertThat(asyncAnnotation).isNotNull
        assertThat(asyncAnnotation.value).isEqualTo("prospeccaoExecutor")
    }

    // Test 4 (PROS-08): Partial failure — one precatorio fetch throws, BFS still completes, other lead persisted
    @Test
    fun `partial CAC failure does not abort BFS — other lead still persisted`() {
        val prospeccao = makeProspeccao()
        val parte1 = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val parte2 = ParteScraped(nome = "Bob", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-FAIL", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-OK", descricao = "Precatório", link = null)

        val scraped = makeProcessoScraped(partes = listOf(parte1, parte2), incidentes = listOf(incidente1, incidente2))
        val processo = makeProcesso()
        val credor1 = makeCredor("Alice", 20L)
        val credor2 = makeCredor("Bob", 21L)
        val prec = makePrecatorio(30L)
        val scored = ScoredResult(total = 50, detalhes = mapOf("total" to 50))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns scraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" }) } returns credor1
        every { credorRepository.findByNomeAndProcessoId("Bob", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Bob" }) } returns credor2
        every { cacScraper.fetchPrecatorio("PREC-FAIL") } throws ScrapingException("CAC timeout")
        every { cacScraper.fetchPrecatorio("PREC-OK") } returns makePrecatorioScraped("PREC-OK")
        every { precatorioRepository.save(any()) } returns prec
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(PROSPECCAO_ID, SEED, 0, 50, null, null, null, null)

        // At least one lead persisted (for PREC-OK)
        verify(atLeast = 1) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
        // finalizarProspeccao called with CONCLUIDA (not ERRO — partial failure is recoverable)
        verify {
            persistenceHelper.finalizarProspeccao(
                prospeccaoId = PROSPECCAO_ID,
                processosVisitados = any(),
                credoresEncontrados = any(),
                leadsQualificados = any(),
                erroMensagem = match { it != null && it.contains("CAC timeout") },
                status = StatusProspeccao.CONCLUIDA
            )
        }
    }

    // Test 5: BFS expansion — seed creditor Alice's name search returns 2 new processes
    @Test
    fun `BFS expansion enqueues and fetches processes found by buscarPorNome`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = emptyList())
        val p2Scraped = makeProcessoScraped(numero = "PROC-002", partes = emptyList(), incidentes = emptyList())
        val p3Scraped = makeProcessoScraped(numero = "PROC-003", partes = emptyList(), incidentes = emptyList())
        val processo = makeProcesso()
        val processo2 = makeProcesso("PROC-002", 11L)
        val processo3 = makeProcesso("PROC-003", 12L)

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { esajScraper.fetchProcesso("PROC-002") } returns p2Scraped
        every { esajScraper.fetchProcesso("PROC-003") } returns p3Scraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.findByNumero("PROC-002") } returns null
        every { processoRepository.findByNumero("PROC-003") } returns null
        every { processoRepository.save(any()) } returnsMany listOf(processo, processo2, processo3)
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(any()) } returns makeCredor()
        every { esajScraper.buscarPorNome("Alice") } returns listOf(
            BuscaResultado(numero = "PROC-002", classe = null, assunto = null, foro = null),
            BuscaResultado(numero = "PROC-003", classe = null, assunto = null, foro = null)
        )
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit

        engine.start(PROSPECCAO_ID, SEED, 1, 50, null, null, null, null)

        verify { esajScraper.fetchProcesso(SEED) }
        verify { esajScraper.fetchProcesso("PROC-002") }
        verify { esajScraper.fetchProcesso("PROC-003") }
    }

    // Test 6 (D-04): Same creditor in two processes with different precatorios — two leads created
    @Test
    fun `same creditor in two processes creates two separate leads`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-A", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-B", descricao = "Precatório", link = null)

        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = listOf(incidente1))
        val p2Scraped = makeProcessoScraped(numero = "PROC-002", partes = listOf(parte), incidentes = listOf(incidente2))
        val processo1 = makeProcesso(SEED, 10L)
        val processo2 = makeProcesso("PROC-002", 11L)
        val credor1 = makeCredor("Alice", 20L)
        val credor2 = makeCredor("Alice", 21L)
        val prec1 = makePrecatorio(30L)
        val prec2 = makePrecatorio(31L)
        val scored = ScoredResult(total = 50, detalhes = mapOf("total" to 50))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { esajScraper.fetchProcesso("PROC-002") } returns p2Scraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.findByNumero("PROC-002") } returns null
        every { processoRepository.save(any()) } returnsMany listOf(processo1, processo2)
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" && it.processo?.id == 10L }) } returns credor1
        every { credorRepository.findByNomeAndProcessoId("Alice", 11L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" && it.processo?.id == 11L }) } returns credor2
        every { cacScraper.fetchPrecatorio("PREC-A") } returns makePrecatorioScraped("PREC-A")
        every { cacScraper.fetchPrecatorio("PREC-B") } returns makePrecatorioScraped("PREC-B")
        every { precatorioRepository.save(any()) } returnsMany listOf(prec1, prec2)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome("Alice") } returns listOf(
            BuscaResultado(numero = "PROC-002", classe = null, assunto = null, foro = null)
        )

        engine.start(PROSPECCAO_ID, SEED, 1, 50, null, null, null, null)

        // Two leads created — one per creditor+precatorio pair
        verify(exactly = 2) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }
}
