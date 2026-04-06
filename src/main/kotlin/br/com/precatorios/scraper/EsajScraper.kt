package br.com.precatorios.scraper

import br.com.precatorios.config.CacheNames
import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.TooManyRequestsException
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

// ---------------------------------------------------------------------------
// Result data classes
// ---------------------------------------------------------------------------

data class ProcessoScraped(
    val numero: String,
    val classe: String?,
    val assunto: String?,
    val foro: String?,
    val vara: String?,
    val juiz: String?,
    val valorAcao: String?,
    val partes: List<ParteScraped>,
    val incidentes: List<IncidenteScraped>,
    val missingFields: List<String>,
    val rawHtml: String
)

data class ParteScraped(
    val nome: String,
    val tipo: String?,
    val advogado: String?
)

data class IncidenteScraped(
    val numero: String?,
    val descricao: String?,
    val link: String?
)

data class BuscaResultado(
    val numero: String,
    val classe: String?,
    val assunto: String?,
    val foro: String?
)

// ---------------------------------------------------------------------------
// Scraper service
// ---------------------------------------------------------------------------

@Service
class EsajScraper(
    private val properties: ScraperProperties,
    @Qualifier("esajRateLimiter") private val esajRateLimiter: RateLimiter,
    @Qualifier("esajRetry") private val esajRetry: Retry
) {

    private val log = LoggerFactory.getLogger(EsajScraper::class.java)

    /**
     * Fetch process details from e-SAJ by process number.
     * Applies rate limiting and retry via Resilience4j programmatic decoration.
     * Returns partial data (null fields + missingFields list) on selector mismatches — never throws on missing HTML.
     */
    @Cacheable(cacheNames = [CacheNames.PROCESSOS], unless = "#result == null")
    fun fetchProcesso(numero: String): ProcessoScraped {
        val retryDecorated = Retry.decorateCheckedSupplier(esajRetry) { doFetchProcesso(numero) }
        val decorated = RateLimiter.decorateCheckedSupplier(esajRateLimiter, retryDecorated)
        return try {
            decorated.get()
        } catch (e: TooManyRequestsException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Search for processes by free-text name in e-SAJ.
     * Applies rate limiting and retry via Resilience4j programmatic decoration.
     */
    fun buscarPorNome(nome: String): List<BuscaResultado> {
        val retryDecorated = Retry.decorateCheckedSupplier(esajRetry) { doBuscarPorNome(nome) }
        val decorated = RateLimiter.decorateCheckedSupplier(esajRateLimiter, retryDecorated)
        return try {
            decorated.get()
        } catch (e: TooManyRequestsException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    // ---------------------------------------------------------------------------
    // Internal fetch methods (called only through the decorated path)
    // ---------------------------------------------------------------------------

    internal fun doFetchProcesso(numero: String): ProcessoScraped {
        val url = "${properties.esaj.baseUrl}${EsajSelectors.SHOW_PATH}"
        log.debug("Fetching processo {} from {}", numero, url)

        val response = Jsoup.connect(url)
            .data("processo.codigo", numero)
            .userAgent(properties.esaj.userAgent)
            .timeout(properties.esaj.timeoutMs)
            .execute()

        if (response.statusCode() == 429) {
            throw TooManyRequestsException("e-SAJ HTTP 429 for processo $numero")
        }

        val doc: Document = response.parse()
        return parseProcesso(numero, doc)
    }

    internal fun doBuscarPorNome(nome: String): List<BuscaResultado> {
        val url = "${properties.esaj.baseUrl}${EsajSelectors.SEARCH_PATH}"
        log.debug("Searching e-SAJ for nome '{}' at {}", nome, url)

        val response = Jsoup.connect(url)
            .data("dadosConsulta.pesquisaLivre", nome)
            .userAgent(properties.esaj.userAgent)
            .timeout(properties.esaj.timeoutMs)
            .execute()

        if (response.statusCode() == 429) {
            throw TooManyRequestsException("e-SAJ HTTP 429 for busca nome '$nome'")
        }

        val doc: Document = response.parse()
        return parseBusca(doc)
    }

    // ---------------------------------------------------------------------------
    // Parsing methods — package-internal for testability
    // ---------------------------------------------------------------------------

    internal fun parseProcesso(numero: String, doc: Document): ProcessoScraped {
        val missingFields = mutableListOf<String>()

        fun extractField(selector: String, fieldName: String): String? {
            val value = doc.select(selector).firstOrNull()?.text()?.takeIf { it.isNotBlank() }
            if (value == null) {
                log.warn("Selector '{}' returned no match for processo {}", selector, numero)
                missingFields.add(fieldName)
            }
            return value
        }

        val classe = extractField(EsajSelectors.CLASSE, "classe")
        val assunto = extractField(EsajSelectors.ASSUNTO, "assunto")
        val foro = extractField(EsajSelectors.FORO, "foro")
        val vara = extractField(EsajSelectors.VARA, "vara")
        val juiz = extractField(EsajSelectors.JUIZ, "juiz")
        val valorAcao = extractField(EsajSelectors.VALOR_ACAO, "valorAcao")

        val partes = parsePartes(doc)
        val incidentes = parseIncidentes(doc)

        return ProcessoScraped(
            numero = numero,
            classe = classe,
            assunto = assunto,
            foro = foro,
            vara = vara,
            juiz = juiz,
            valorAcao = valorAcao,
            partes = partes,
            incidentes = incidentes,
            missingFields = missingFields,
            rawHtml = doc.outerHtml()
        )
    }

    internal fun parsePartes(doc: Document): List<ParteScraped> {
        val table = doc.select(EsajSelectors.PARTES_TABLE).firstOrNull() ?: return emptyList()
        val partes = mutableListOf<ParteScraped>()

        for (row in table.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            // First cell contains tipo (e.g. "Reqte:", "Reqdo:", "Exequente:")
            val tipo = cells.getOrNull(0)?.text()?.trim()?.removeSuffix(":").takeIf { it?.isNotBlank() == true }

            // Second cell contains party name and possibly counsel
            val nomeCell = cells.getOrNull(1) ?: continue
            val nomeElements = nomeCell.select(EsajSelectors.PARTE_NOME)

            if (nomeElements.isEmpty()) {
                // Fallback: use the full cell text
                val fullText = nomeCell.text().trim()
                if (fullText.isNotBlank()) {
                    partes.add(ParteScraped(nome = fullText, tipo = tipo, advogado = null))
                }
            } else {
                // First element is the party name, subsequent elements are counsel
                val nome = nomeElements.first()?.text()?.trim() ?: continue
                if (nome.isBlank()) continue

                val advogado = nomeElements.drop(1)
                    .joinToString("; ") { it.text().trim() }
                    .takeIf { it.isNotBlank() }

                partes.add(ParteScraped(nome = nome, tipo = tipo, advogado = advogado))
            }
        }

        return partes
    }

    internal fun parseIncidentes(doc: Document): List<IncidenteScraped> {
        val table = doc.select(EsajSelectors.INCIDENTES_TABLE).firstOrNull() ?: return emptyList()
        val incidentes = mutableListOf<IncidenteScraped>()

        for (row in table.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            val link = row.select("a").firstOrNull()
            val linkHref = link?.attr("href")?.takeIf { it.isNotBlank() }
            val numero = link?.text()?.trim()?.takeIf { it.isNotBlank() }
            val descricao = cells.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: cells.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }

            if (numero != null || descricao != null) {
                incidentes.add(
                    IncidenteScraped(
                        numero = numero,
                        descricao = descricao,
                        link = linkHref
                    )
                )
            }
        }

        return incidentes
    }

    internal fun parseBusca(doc: Document): List<BuscaResultado> {
        val table = doc.select(EsajSelectors.SEARCH_RESULT_TABLE).firstOrNull() ?: return emptyList()
        val resultados = mutableListOf<BuscaResultado>()

        for (row in table.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            // Typical e-SAJ search result row: numero | classe | assunto | foro
            val numero = cells.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val classe = cells.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }
            val assunto = cells.getOrNull(2)?.text()?.trim()?.takeIf { it.isNotBlank() }
            val foro = cells.getOrNull(3)?.text()?.trim()?.takeIf { it.isNotBlank() }

            resultados.add(BuscaResultado(numero = numero, classe = classe, assunto = assunto, foro = foro))
        }

        return resultados
    }
}
