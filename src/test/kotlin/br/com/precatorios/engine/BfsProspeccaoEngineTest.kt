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

    // ===== Plan 02: Filters, depth control, maxCredores, maxSearchResults =====

    // Test 7 (PROS-02): BFS with profundidadeMaxima=1 — seed at depth 0 is visited and expanded,
    // processes at depth 1 are visited but NOT expanded (buscarPorNome not called for depth-1 parties)
    @Test
    fun `profundidadeMaxima=1 expands seed but not depth-1 processes`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = emptyList())
        val p2Scraped = makeProcessoScraped(numero = "PROC-002", partes = listOf(parte), incidentes = emptyList())
        val processo = makeProcesso()
        val processo2 = makeProcesso("PROC-002", 11L)

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { esajScraper.fetchProcesso("PROC-002") } returns p2Scraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.findByNumero("PROC-002") } returns null
        every { processoRepository.save(any()) } returnsMany listOf(processo, processo2)
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.findByNomeAndProcessoId("Alice", 11L) } returns null
        every { credorRepository.save(any()) } returns makeCredor()
        // buscarPorNome for seed (depth=0 < profundidadeMaxima=1) -> returns PROC-002
        every { esajScraper.buscarPorNome("Alice") } returns listOf(
            BuscaResultado(numero = "PROC-002", classe = null, assunto = null, foro = null)
        )
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit

        engine.start(PROSPECCAO_ID, SEED, profundidadeMaxima = 1, maxCredores = 50, null, null, null, null)

        // Seed (depth=0) is expanded — buscarPorNome called once for "Alice" from seed
        verify(exactly = 1) { esajScraper.buscarPorNome("Alice") }
        // PROC-002 (depth=1) is visited but NOT expanded
        verify { esajScraper.fetchProcesso("PROC-002") }
    }

    // Test 8 (PROS-02): BFS with profundidadeMaxima=0 — only seed is visited, no expansion at all
    @Test
    fun `profundidadeMaxima=0 visits only seed process — no buscarPorNome calls`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = emptyList())
        val processo = makeProcesso()

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(any()) } returns makeCredor()
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit

        engine.start(PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 50, null, null, null, null)

        // No buscarPorNome calls at all
        verify(exactly = 0) { esajScraper.buscarPorNome(any()) }
        // Only seed was fetched
        verify(exactly = 1) { esajScraper.fetchProcesso(SEED) }
    }

    // Test 9 (PROS-03): maxCredores=2 — seed has 3 creditors with precatorios, only 2 leads persisted
    @Test
    fun `maxCredores=2 stops lead creation after 2 leads and terminates BFS`() {
        val prospeccao = makeProspeccao()
        val parte1 = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val parte2 = ParteScraped(nome = "Bob", tipo = "Exequente", advogado = null)
        val parte3 = ParteScraped(nome = "Carol", tipo = "Exequente", advogado = null)
        val incidente = IncidenteScraped(numero = "PREC-001", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(
            partes = listOf(parte1, parte2, parte3),
            incidentes = listOf(incidente)
        )
        val processo = makeProcesso()
        val credor1 = makeCredor("Alice", 20L)
        val credor2 = makeCredor("Bob", 21L)
        val credor3 = makeCredor("Carol", 22L)
        val prec = makePrecatorio(30L)
        val scored = ScoredResult(total = 60, detalhes = mapOf("total" to 60))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" }) } returns credor1
        every { credorRepository.findByNomeAndProcessoId("Bob", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Bob" }) } returns credor2
        every { credorRepository.findByNomeAndProcessoId("Carol", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Carol" }) } returns credor3
        every { cacScraper.fetchPrecatorio("PREC-001") } returns makePrecatorioScraped("PREC-001")
        every { precatorioRepository.save(any()) } returns prec
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 2, null, null, null, null)

        // Only 2 leads should be persisted (maxCredores=2)
        verify(exactly = 2) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }

    // Test 10 (PROS-05 D-09): valorMinimo filter — low value precatorio skipped, high value persisted
    @Test
    fun `valorMinimo filter skips precatorio below threshold and persists above threshold`() {
        val prospeccao = makeProspeccao()
        val parte1 = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val parte2 = ParteScraped(nome = "Bob", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-LOW", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-HIGH", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(
            partes = listOf(parte1, parte2),
            incidentes = listOf(incidente1, incidente2)
        )
        val processo = makeProcesso()
        val credor1 = makeCredor("Alice", 20L)
        val credor2 = makeCredor("Bob", 21L)
        val precLow = makePrecatorio(30L).apply { valorAtualizado = java.math.BigDecimal("50000") }
        val precHigh = makePrecatorio(31L).apply { valorAtualizado = java.math.BigDecimal("200000") }
        val scored = ScoredResult(total = 70, detalhes = mapOf("total" to 70))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" }) } returns credor1
        every { credorRepository.findByNomeAndProcessoId("Bob", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Bob" }) } returns credor2
        every { cacScraper.fetchPrecatorio("PREC-LOW") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-LOW", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "50000", valorAtualizado = "50000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 100, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { cacScraper.fetchPrecatorio("PREC-HIGH") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-HIGH", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "200000", valorAtualizado = "200000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        // 2 parties × 2 incidentes = 4 save calls: Alice[PREC-LOW, PREC-HIGH], Bob[PREC-LOW, PREC-HIGH]
        every { precatorioRepository.save(any()) } returnsMany listOf(precLow, precHigh, precLow, precHigh)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(
            PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 50,
            entidadesDevedoras = null, valorMinimo = java.math.BigDecimal("100000"),
            apenasAlimentar = null, apenasPendentes = null
        )

        // Only the high-value precatorio should produce a lead
        // precLow (50000 < 100000) is skipped, precHigh (200000 >= 100000) passes
        // Alice: PREC-LOW (filtered) + PREC-HIGH (pass) = 1 lead
        // Bob: PREC-LOW (filtered) + PREC-HIGH (pass) = 1 lead
        // Total = 2 leads
        verify(exactly = 2) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }

    // Test 11 (PROS-05 D-09): entidadesDevedoras filter — matching entity passes, non-matching skipped
    @Test
    fun `entidadesDevedoras filter passes matching debtor and skips non-matching`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-FAZENDA", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-MUNICIPIO", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = listOf(incidente1, incidente2))
        val processo = makeProcesso()
        val credor = makeCredor("Alice", 20L)
        val precFazenda = makePrecatorio(30L).apply { entidadeDevedora = "Fazenda do Estado de SP" }
        val precMunicipio = makePrecatorio(31L).apply { entidadeDevedora = "Municipio de Campinas" }
        val scored = ScoredResult(total = 70, detalhes = mapOf("total" to 70))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(any()) } returns credor
        every { cacScraper.fetchPrecatorio("PREC-FAZENDA") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-FAZENDA", numeroProcesso = SEED,
            entidadeDevedora = "Fazenda do Estado de SP",
            valorOriginal = "100000", valorAtualizado = "120000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { cacScraper.fetchPrecatorio("PREC-MUNICIPIO") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-MUNICIPIO", numeroProcesso = SEED,
            entidadeDevedora = "Municipio de Campinas",
            valorOriginal = "100000", valorAtualizado = "120000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { precatorioRepository.save(any()) } returnsMany listOf(precFazenda, precMunicipio)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(
            PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 50,
            entidadesDevedoras = listOf("FAZENDA"),
            valorMinimo = null, apenasAlimentar = null, apenasPendentes = null
        )

        // Only FAZENDA precatorio passes the filter
        verify(exactly = 1) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }

    // Test 12 (PROS-05 D-09): apenasAlimentar=true filter — ALIMENTAR passes, COMUM skipped
    @Test
    fun `apenasAlimentar=true filter passes ALIMENTAR and skips COMUM`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-ALIMENTAR", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-COMUM", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = listOf(incidente1, incidente2))
        val processo = makeProcesso()
        val credor = makeCredor("Alice", 20L)
        val precAlimentar = makePrecatorio(30L).apply { natureza = "ALIMENTAR" }
        val precComum = makePrecatorio(31L).apply { natureza = "COMUM" }
        val scored = ScoredResult(total = 70, detalhes = mapOf("total" to 70))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(any()) } returns credor
        every { cacScraper.fetchPrecatorio("PREC-ALIMENTAR") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-ALIMENTAR", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "100000", valorAtualizado = "120000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { cacScraper.fetchPrecatorio("PREC-COMUM") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-COMUM", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "100000", valorAtualizado = "120000", natureza = "COMUM",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { precatorioRepository.save(any()) } returnsMany listOf(precAlimentar, precComum)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(
            PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 50,
            entidadesDevedoras = null, valorMinimo = null, apenasAlimentar = true, apenasPendentes = null
        )

        // Only ALIMENTAR precatorio passes the filter
        verify(exactly = 1) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }

    // Test 13 (PROS-05 D-09): apenasPendentes=true filter — PENDENTE passes, PAGO skipped
    @Test
    fun `apenasPendentes=true filter passes PENDENTE and skips PAGO`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val incidente1 = IncidenteScraped(numero = "PREC-PENDENTE", descricao = "Precatório", link = null)
        val incidente2 = IncidenteScraped(numero = "PREC-PAGO", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = listOf(incidente1, incidente2))
        val processo = makeProcesso()
        val credor = makeCredor("Alice", 20L)
        val precPendente = makePrecatorio(30L).apply { statusPagamento = "PENDENTE" }
        val precPago = makePrecatorio(31L).apply { statusPagamento = "PAGO" }
        val scored = ScoredResult(total = 70, detalhes = mapOf("total" to 70))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(any()) } returns credor
        every { cacScraper.fetchPrecatorio("PREC-PENDENTE") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-PENDENTE", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "100000", valorAtualizado = "120000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { cacScraper.fetchPrecatorio("PREC-PAGO") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-PAGO", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "100000", valorAtualizado = "120000", natureza = "ALIMENTAR",
            statusPagamento = "PAGO", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { precatorioRepository.save(any()) } returnsMany listOf(precPendente, precPago)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(
            PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 50,
            entidadesDevedoras = null, valorMinimo = null, apenasAlimentar = null, apenasPendentes = true
        )

        // Only PENDENTE precatorio passes the filter
        verify(exactly = 1) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
    }

    // Test 14 (D-10): Filtered-out creditor still explored for BFS expansion
    @Test
    fun `filtered-out creditor still contributes to BFS expansion`() {
        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val incidente = IncidenteScraped(numero = "PREC-LOW", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = listOf(incidente))
        val p2Scraped = makeProcessoScraped(numero = "PROC-002", partes = emptyList(), incidentes = emptyList())
        val processo = makeProcesso()
        val processo2 = makeProcesso("PROC-002", 11L)
        val credor = makeCredor("Alice", 20L)
        val precLow = makePrecatorio(30L).apply { valorAtualizado = java.math.BigDecimal("50000") }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { esajScraper.fetchProcesso("PROC-002") } returns p2Scraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.findByNumero("PROC-002") } returns null
        every { processoRepository.save(any()) } returnsMany listOf(processo, processo2)
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(any()) } returns credor
        every { cacScraper.fetchPrecatorio("PREC-LOW") } returns PrecatorioScraped(
            numeroPrecatorio = "PREC-LOW", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
            valorOriginal = "50000", valorAtualizado = "50000", natureza = "ALIMENTAR",
            statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
            missingFields = emptyList(), rawHtml = ""
        )
        every { precatorioRepository.save(any()) } returns precLow
        // buscarPorNome for Alice returns PROC-002 (Alice's precatorio is filtered but expansion still happens)
        every { esajScraper.buscarPorNome("Alice") } returns listOf(
            BuscaResultado(numero = "PROC-002", classe = null, assunto = null, foro = null)
        )
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit

        engine.start(
            PROSPECCAO_ID, SEED, profundidadeMaxima = 1, maxCredores = 50,
            entidadesDevedoras = null, valorMinimo = java.math.BigDecimal("100000"),
            apenasAlimentar = null, apenasPendentes = null
        )

        // Low-value precatorio filtered — no lead persisted
        verify(exactly = 0) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
        // But BFS expansion still happened — PROC-002 was fetched
        verify { esajScraper.fetchProcesso("PROC-002") }
        // buscarPorNome still called for the filtered creditor
        verify { esajScraper.buscarPorNome("Alice") }
    }

    // Test 15 (D-11): credoresEncontrados counts only filter-passing leads
    @Test
    fun `credoresEncontrados counts only filter-passing leads`() {
        val prospeccao = makeProspeccao()
        val parte1 = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val parte2 = ParteScraped(nome = "Bob", tipo = "Exequente", advogado = null)
        val parte3 = ParteScraped(nome = "Carol", tipo = "Exequente", advogado = null)
        val incidente = IncidenteScraped(numero = "PREC-001", descricao = "Precatório", link = null)
        val seedScraped = makeProcessoScraped(
            partes = listOf(parte1, parte2, parte3),
            incidentes = listOf(incidente)
        )
        val processo = makeProcesso()
        val credor1 = makeCredor("Alice", 20L)
        val credor2 = makeCredor("Bob", 21L)
        val credor3 = makeCredor("Carol", 22L)
        // Alice and Bob have high value (pass filter), Carol has low value (fails filter)
        val precHigh1 = makePrecatorio(30L).apply { valorAtualizado = java.math.BigDecimal("200000") }
        val precHigh2 = makePrecatorio(31L).apply { valorAtualizado = java.math.BigDecimal("150000") }
        val precLow = makePrecatorio(32L).apply { valorAtualizado = java.math.BigDecimal("10000") }
        val scored = ScoredResult(total = 70, detalhes = mapOf("total" to 70))
        val lead = Lead().apply { id = 100L }

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { processoRepository.findByNumero(SEED) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Alice" }) } returns credor1
        every { credorRepository.findByNomeAndProcessoId("Bob", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Bob" }) } returns credor2
        every { credorRepository.findByNomeAndProcessoId("Carol", 10L) } returns null
        every { credorRepository.save(match { it.nome == "Carol" }) } returns credor3
        every { cacScraper.fetchPrecatorio("PREC-001") } returnsMany listOf(
            PrecatorioScraped(
                numeroPrecatorio = "PREC-001", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
                valorOriginal = "200000", valorAtualizado = "200000", natureza = "ALIMENTAR",
                statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
                missingFields = emptyList(), rawHtml = ""
            ),
            PrecatorioScraped(
                numeroPrecatorio = "PREC-001", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
                valorOriginal = "150000", valorAtualizado = "150000", natureza = "ALIMENTAR",
                statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
                missingFields = emptyList(), rawHtml = ""
            ),
            PrecatorioScraped(
                numeroPrecatorio = "PREC-001", numeroProcesso = SEED, entidadeDevedora = "FAZENDA",
                valorOriginal = "10000", valorAtualizado = "10000", natureza = "ALIMENTAR",
                statusPagamento = "PENDENTE", posicaoCronologica = 50, dataExpedicao = null,
                missingFields = emptyList(), rawHtml = ""
            )
        )
        every { precatorioRepository.save(any()) } returnsMany listOf(precHigh1, precHigh2, precLow)
        every { scoringService.score(any()) } returns scored
        every { persistenceHelper.persistirLead(any(), any(), any(), any()) } returns lead
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit
        every { esajScraper.buscarPorNome(any()) } returns emptyList()

        engine.start(
            PROSPECCAO_ID, SEED, profundidadeMaxima = 0, maxCredores = 50,
            entidadesDevedoras = null, valorMinimo = java.math.BigDecimal("100000"),
            apenasAlimentar = null, apenasPendentes = null
        )

        // Only Alice and Bob pass the filter (2 leads); Carol is filtered out
        verify(exactly = 2) { persistenceHelper.persistirLead(any(), any(), any(), any()) }
        // finalizarProspeccao called with credoresEncontrados=2 (not 3)
        verify {
            persistenceHelper.finalizarProspeccao(
                prospeccaoId = PROSPECCAO_ID,
                processosVisitados = any(),
                credoresEncontrados = 2,
                leadsQualificados = 2,
                erroMensagem = any(),
                status = StatusProspeccao.CONCLUIDA
            )
        }
    }

    // Test 16 (D-03): buscarPorNome returns 15 results but only first maxSearchResults=10 are enqueued
    @Test
    fun `buscarPorNome results are capped at maxSearchResults`() {
        // Engine with maxSearchResults=3 to simplify verification
        val smallEngine = BfsProspeccaoEngine(
            esajScraper = esajScraper,
            cacScraper = cacScraper,
            scoringService = scoringService,
            persistenceHelper = persistenceHelper,
            prospeccaoRepository = prospeccaoRepository,
            processoRepository = processoRepository,
            credorRepository = credorRepository,
            precatorioRepository = precatorioRepository,
            maxSearchResults = 3
        )

        val prospeccao = makeProspeccao()
        val parte = ParteScraped(nome = "Alice", tipo = "Exequente", advogado = null)
        val seedScraped = makeProcessoScraped(partes = listOf(parte), incidentes = emptyList())
        val processo = makeProcesso()

        // buscarPorNome returns 5 results
        val searchResults = (1..5).map { i ->
            BuscaResultado(numero = "PROC-00$i", classe = null, assunto = null, foro = null)
        }
        val emptyScraped = makeProcessoScraped(numero = "PROC-001", partes = emptyList(), incidentes = emptyList())

        every { prospeccaoRepository.findById(PROSPECCAO_ID) } returns Optional.of(prospeccao)
        every { esajScraper.fetchProcesso(SEED) } returns seedScraped
        every { esajScraper.fetchProcesso(match { it.startsWith("PROC-") }) } returns emptyScraped
        every { processoRepository.findByNumero(any()) } returns null
        every { processoRepository.save(any()) } returns processo
        every { credorRepository.findByNomeAndProcessoId("Alice", any()) } returns null
        every { credorRepository.save(any()) } returns makeCredor()
        every { esajScraper.buscarPorNome("Alice") } returns searchResults
        every { persistenceHelper.atualizarContadores(any(), any(), any(), any()) } returns Unit
        every { persistenceHelper.finalizarProspeccao(any(), any(), any(), any(), any(), any()) } returns Unit

        smallEngine.start(PROSPECCAO_ID, SEED, profundidadeMaxima = 1, maxCredores = 50, null, null, null, null)

        // Only first 3 processes from buscarPorNome results should be fetched (maxSearchResults=3)
        verify(exactly = 1) { esajScraper.fetchProcesso("PROC-001") }
        verify(exactly = 1) { esajScraper.fetchProcesso("PROC-002") }
        verify(exactly = 1) { esajScraper.fetchProcesso("PROC-003") }
        verify(exactly = 0) { esajScraper.fetchProcesso("PROC-004") }
        verify(exactly = 0) { esajScraper.fetchProcesso("PROC-005") }
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
