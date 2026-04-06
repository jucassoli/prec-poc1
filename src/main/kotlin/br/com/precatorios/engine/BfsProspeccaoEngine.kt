package br.com.precatorios.engine

import br.com.precatorios.domain.Credor
import br.com.precatorios.domain.Precatorio
import br.com.precatorios.domain.Processo
import br.com.precatorios.domain.enums.StatusProspeccao
import br.com.precatorios.repository.CredorRepository
import br.com.precatorios.repository.PrecatorioRepository
import br.com.precatorios.repository.ProcessoRepository
import br.com.precatorios.repository.ProspeccaoRepository
import br.com.precatorios.scraper.CacScraper
import br.com.precatorios.scraper.EsajScraper
import br.com.precatorios.scraper.PrecatorioScraped
import br.com.precatorios.service.ScoringService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class BfsProspeccaoEngine(
    private val esajScraper: EsajScraper,
    private val cacScraper: CacScraper,
    private val scoringService: ScoringService,
    private val persistenceHelper: ProspeccaoLeadPersistenceHelper,
    private val prospeccaoRepository: ProspeccaoRepository,
    private val processoRepository: ProcessoRepository,
    private val credorRepository: CredorRepository,
    private val precatorioRepository: PrecatorioRepository,
    @Value("\${prospeccao.max-search-results-per-creditor:10}") private val maxSearchResults: Int
) {

    private val log = LoggerFactory.getLogger(BfsProspeccaoEngine::class.java)

    @Async("prospeccaoExecutor")
    fun start(
        prospeccaoId: Long,
        processoSemente: String,
        profundidadeMaxima: Int,
        maxCredores: Int,
        entidadesDevedoras: List<String>?,
        valorMinimo: BigDecimal?,
        apenasAlimentar: Boolean?,
        apenasPendentes: Boolean?
    ) {
        // CRITICAL: all mutable state is local — this bean is a singleton
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val errors = mutableListOf<String>()
        var processosVisitados = 0
        var credoresEncontrados = 0
        var leadsQualificados = 0

        visited.add(processoSemente)
        queue.add(Pair(processoSemente, 0))

        val prospeccao = prospeccaoRepository.findById(prospeccaoId)
            .orElseThrow { IllegalArgumentException("Prospecção não encontrada: $prospeccaoId") }

        try {
            while (queue.isNotEmpty()) {
                if (credoresEncontrados >= maxCredores) break

                val (numero, depth) = queue.removeFirst()

                try {
                    val scraped = esajScraper.fetchProcesso(numero)
                    processosVisitados++

                    // Persist or load Processo entity
                    val processo = processoRepository.findByNumero(numero) ?: run {
                        val novo = Processo().apply {
                            this.numero = numero
                            this.classe = scraped.classe
                            this.assunto = scraped.assunto
                            this.foro = scraped.foro
                            this.vara = scraped.vara
                            this.juiz = scraped.juiz
                            this.valorAcao = scraped.valorAcao
                            this.dadosBrutos = scraped.rawHtml
                            this.dataColeta = LocalDateTime.now()
                        }
                        processoRepository.save(novo)
                    }

                    // Process each party (creditor)
                    for (parte in scraped.partes) {
                        if (credoresEncontrados >= maxCredores) break

                        // Find or create Credor
                        val credor = credorRepository.findByNomeAndProcessoId(parte.nome, processo.id!!)
                            ?: run {
                                val novo = Credor().apply {
                                    this.nome = parte.nome
                                    this.tipoParticipacao = parte.tipo
                                    this.advogado = parte.advogado
                                    this.dataDescoberta = LocalDateTime.now()
                                    this.processo = processo
                                }
                                credorRepository.save(novo)
                            }

                        // Process each precatorio incident for this party
                        for (incidente in scraped.incidentes) {
                            if (incidente.numero == null) continue

                            try {
                                val precScraped = cacScraper.fetchPrecatorio(incidente.numero)
                                val precatorio = toPrecatorioEntity(precScraped, numero, credor)
                                val savedPrecatorio = precatorioRepository.save(precatorio)

                                if (passaFiltros(savedPrecatorio, entidadesDevedoras, valorMinimo, apenasAlimentar, apenasPendentes)) {
                                    val scored = scoringService.score(savedPrecatorio)
                                    persistenceHelper.persistirLead(prospeccao, credor, savedPrecatorio, scored)
                                    credoresEncontrados++
                                    leadsQualificados++
                                }
                            } catch (e: Exception) {
                                log.warn("BFS incidente {} failed for processo {}: {}", incidente.numero, numero, e.message)
                                errors.add("${incidente.numero}: ${e.message}")
                            }
                        }
                    }

                    // BFS expansion: enqueue new processes from name searches
                    if (depth < profundidadeMaxima) {
                        for (parte in scraped.partes) {
                            try {
                                val results = esajScraper.buscarPorNome(parte.nome).take(maxSearchResults)
                                for (result in results) {
                                    if (result.numero !in visited) {
                                        visited.add(result.numero)
                                        queue.add(Pair(result.numero, depth + 1))
                                    }
                                }
                            } catch (e: Exception) {
                                log.warn("BFS buscarPorNome for '{}' failed: {}", parte.nome, e.message)
                                errors.add("buscarPorNome(${parte.nome}): ${e.message}")
                            }
                        }
                    }

                    // Update counters after each node
                    persistenceHelper.atualizarContadores(prospeccaoId, processosVisitados, credoresEncontrados, leadsQualificados)

                } catch (e: Exception) {
                    log.warn("BFS node {} failed: {}", numero, e.message)
                    errors.add("$numero: ${e.message}")
                }
            }

            // Finalize with CONCLUIDA
            persistenceHelper.finalizarProspeccao(
                prospeccaoId = prospeccaoId,
                processosVisitados = processosVisitados,
                credoresEncontrados = credoresEncontrados,
                leadsQualificados = leadsQualificados,
                erroMensagem = if (errors.isEmpty()) null else errors.joinToString("\n"),
                status = StatusProspeccao.CONCLUIDA
            )

        } catch (e: Exception) {
            log.error("BFS engine unexpected failure for prospeccao {}: {}", prospeccaoId, e.message, e)
            persistenceHelper.finalizarProspeccao(
                prospeccaoId = prospeccaoId,
                processosVisitados = processosVisitados,
                credoresEncontrados = credoresEncontrados,
                leadsQualificados = leadsQualificados,
                erroMensagem = e.message,
                status = StatusProspeccao.ERRO
            )
        }
    }

    private fun toPrecatorioEntity(
        scraped: PrecatorioScraped,
        numeroProcesso: String,
        credor: Credor
    ): Precatorio {
        return Precatorio().apply {
            this.numeroPrecatorio = scraped.numeroPrecatorio
            this.numeroProcesso = numeroProcesso
            this.entidadeDevedora = scraped.entidadeDevedora
            this.valorOriginal = scraped.valorOriginal?.toBigDecimalOrNull()
            this.valorAtualizado = scraped.valorAtualizado?.toBigDecimalOrNull()
            this.natureza = scraped.natureza
            this.statusPagamento = scraped.statusPagamento
            this.posicaoCronologica = scraped.posicaoCronologica
            this.dadosBrutos = scraped.rawHtml
            this.dataColeta = LocalDateTime.now()
            this.credor = credor
        }
    }

    /**
     * Filter predicate — D-09: filters applied during BFS traversal.
     * Non-matching leads are not scored or persisted.
     * Filters only affect lead creation, NOT BFS expansion (D-10).
     */
    private fun passaFiltros(
        precatorio: Precatorio,
        entidadesDevedoras: List<String>?,
        valorMinimo: BigDecimal?,
        apenasAlimentar: Boolean?,
        apenasPendentes: Boolean?
    ): Boolean {
        // entidadesDevedoras: if filter specified, precatorio.entidadeDevedora must contain at least one
        if (!entidadesDevedoras.isNullOrEmpty()) {
            val devedora = precatorio.entidadeDevedora?.uppercase() ?: return false
            if (entidadesDevedoras.none { devedora.contains(it.uppercase()) }) return false
        }
        // valorMinimo: precatorio.valorAtualizado must be >= valorMinimo
        if (valorMinimo != null && (precatorio.valorAtualizado == null || precatorio.valorAtualizado!! < valorMinimo)) return false
        // apenasAlimentar: precatorio.natureza must contain "ALIMENTAR"
        if (apenasAlimentar == true && precatorio.natureza?.uppercase()?.contains("ALIMENTAR") != true) return false
        // apenasPendentes: precatorio.statusPagamento must be "PENDENTE" (case-insensitive)
        if (apenasPendentes == true && precatorio.statusPagamento?.uppercase() != "PENDENTE") return false
        return true
    }
}
