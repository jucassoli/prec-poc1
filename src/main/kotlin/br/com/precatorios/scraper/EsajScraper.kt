package br.com.precatorios.scraper

import br.com.precatorios.config.CacheNames
import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.TooManyRequestsException
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.jsoup.Connection
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
    val foro: String?,
    val processoCodigo: String? = null,
    val processoForo: String? = null,
    val urlEsaj: String? = null
)

private data class NumeroProcessoUnificado(
    val numeroDigitoAno: String,
    val foroNumero: String,
    val numeroCompleto: String
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

    fun buscarPorNumero(numero: String): List<BuscaResultado> {
        val retryDecorated = Retry.decorateCheckedSupplier(esajRetry) { doBuscarPorNumero(numero) }
        val decorated = RateLimiter.decorateCheckedSupplier(esajRateLimiter, retryDecorated)
        return try {
            decorated.get()
        } catch (e: TooManyRequestsException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    fun buscarPorCpf(cpf: String): List<BuscaResultado> {
        val retryDecorated = Retry.decorateCheckedSupplier(esajRetry) { doBuscarPorCpf(cpf) }
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
        val resultadosBusca = doBuscarPorNumero(numero)
        val resultadoBusca = resultadosBusca.firstOrNull { it.numero == numero }
            ?: resultadosBusca.firstOrNull()
            ?: return parseProcesso(numero, Jsoup.parse("<html><body></body></html>"))

        val response = fetchDetalheProcesso(resultadoBusca)

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
            .method(Connection.Method.GET)
            .data("cbPesquisa", "NMPART")
            .data("dadosConsulta.valorConsulta", nome)
            .userAgent(properties.esaj.userAgent)
            .timeout(properties.esaj.timeoutMs)
            .execute()

        if (response.statusCode() == 429) {
            throw TooManyRequestsException("e-SAJ HTTP 429 for busca nome '$nome'")
        }

        val doc: Document = response.parse()
        return parseBusca(doc)
    }

    internal fun doBuscarPorNumero(numero: String): List<BuscaResultado> {
        val partes = parseNumeroProcessoUnificado(numero)
        val url = "${properties.esaj.baseUrl}${EsajSelectors.SEARCH_PATH}"
        log.debug("Searching e-SAJ for numero '{}' at {}", numero, url)

        val response = Jsoup.connect(url)
            .method(Connection.Method.GET)
            .data("conversationId", "")
            .data("cbPesquisa", "NUMPROC")
            .data("numeroDigitoAnoUnificado", partes.numeroDigitoAno)
            .data("foroNumeroUnificado", partes.foroNumero)
            .data("dadosConsulta.valorConsultaNuUnificado", partes.numeroCompleto)
            .data("dadosConsulta.valorConsulta", "")
            .data("dadosConsulta.tipoNuProcesso", "UNIFICADO")
            .userAgent(properties.esaj.userAgent)
            .timeout(properties.esaj.timeoutMs)
            .execute()

        if (response.statusCode() == 429) {
            throw TooManyRequestsException("e-SAJ HTTP 429 for busca numero '$numero'")
        }

        return parseBusca(response.parse())
    }

    internal fun doBuscarPorCpf(cpf: String): List<BuscaResultado> {
        val url = "${properties.esaj.baseUrl}${EsajSelectors.SEARCH_PATH}"
        log.debug("Searching e-SAJ for cpf '{}' at {}", cpf, url)

        val response = Jsoup.connect(url)
            .method(Connection.Method.GET)
            .data("cbPesquisa", "DOCPART")
            .data("dadosConsulta.valorConsulta", cpf)
            .userAgent(properties.esaj.userAgent)
            .timeout(properties.esaj.timeoutMs)
            .execute()

        if (response.statusCode() == 429) {
            throw TooManyRequestsException("e-SAJ HTTP 429 for busca cpf '$cpf'")
        }

        return parseBusca(response.parse())
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
            val nome = nomeCell.textNodes()
                .map { it.text().trim() }
                .firstOrNull { it.isNotBlank() }
                ?: nomeCell.ownText().trim().takeIf { it.isNotBlank() }
                ?: continue

            val advogado = nomeCell.text()
                .substringAfter("Advogado:", "")
                .trim()
                .takeIf { it.isNotBlank() && it != nome }

            partes.add(ParteScraped(nome = nome, tipo = tipo, advogado = advogado))
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
        val resultados = mutableListOf<BuscaResultado>()

        for (foroHeader in doc.select(EsajSelectors.SEARCH_FORO_HEADER)) {
            val foro = foroHeader.text().trim().takeIf { it.isNotBlank() }
            val list = foroHeader.nextElementSibling()?.takeIf { it.tagName() == "ul" } ?: continue

            for (item in list.children().filter { it.tagName() == "li" }) {
                val link = item.select(EsajSelectors.SEARCH_PROCESS_LINK).firstOrNull() ?: continue
                val href = link.attr("href").takeIf { it.isNotBlank() }
                val numero = link.text().trim().takeIf { it.isNotBlank() } ?: continue
                val classe = item.select(EsajSelectors.SEARCH_PROCESS_CLASS).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
                val assunto = item.select(EsajSelectors.SEARCH_PROCESS_SUBJECT).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
                val (processoCodigo, processoForo) = extractProcessoParams(href)
                val absoluteUrl = href?.let { buildAbsoluteUrl(it) }

                resultados.add(
                    BuscaResultado(
                        numero = numero,
                        classe = classe,
                        assunto = assunto,
                        foro = foro,
                        processoCodigo = processoCodigo,
                        processoForo = processoForo,
                        urlEsaj = absoluteUrl
                    )
                )
            }
        }

        return resultados
    }

    private fun fetchDetalheProcesso(resultadoBusca: BuscaResultado): Connection.Response {
        val urlCompleta = resultadoBusca.urlEsaj?.takeIf { it.isNotBlank() }
        if (urlCompleta != null) {
            log.debug("Fetching processo {} using search result URL {}", resultadoBusca.numero, urlCompleta)
            return Jsoup.connect(urlCompleta)
                .method(Connection.Method.GET)
                .userAgent(properties.esaj.userAgent)
                .timeout(properties.esaj.timeoutMs)
                .execute()
        }

        val processoCodigo = resultadoBusca.processoCodigo
            ?: throw IllegalStateException("Resultado de busca sem processo.codigo para ${resultadoBusca.numero}")
        val processoForo = resultadoBusca.processoForo
            ?: throw IllegalStateException("Resultado de busca sem processo.foro para ${resultadoBusca.numero}")
        val url = "${properties.esaj.baseUrl}${EsajSelectors.SHOW_PATH}"
        log.debug("Fetching processo {} from {} using codigo {} and foro {}", resultadoBusca.numero, url, processoCodigo, processoForo)

        return Jsoup.connect(url)
            .method(Connection.Method.GET)
            .data("processo.codigo", processoCodigo)
            .data("processo.foro", processoForo)
            .userAgent(properties.esaj.userAgent)
            .timeout(properties.esaj.timeoutMs)
            .execute()
    }

    private fun parseNumeroProcessoUnificado(numero: String): NumeroProcessoUnificado {
        val match = Regex("^([0-9]{7}-[0-9]{2}\\.[0-9]{4})\\.[0-9]\\.[0-9]{2}\\.([0-9]{4})$").matchEntire(numero)
            ?: throw IllegalArgumentException("Formato CNJ invalido. Esperado: NNNNNNN-DD.AAAA.J.TR.OOOO")

        return NumeroProcessoUnificado(
            numeroDigitoAno = match.groupValues[1],
            foroNumero = match.groupValues[2],
            numeroCompleto = numero
        )
    }

    private fun extractProcessoParams(href: String?): Pair<String?, String?> {
        if (href.isNullOrBlank()) return null to null
        val query = href.substringAfter('?', "")
        if (query.isBlank()) return null to null

        var processoCodigo: String? = null
        var processoForo: String? = null
        for (pair in query.split("&")) {
            val (key, value) = pair.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            when (key) {
                "processo.codigo" -> processoCodigo = value.ifBlank { null }
                "processo.foro" -> processoForo = value.ifBlank { null }
            }
        }
        return processoCodigo to processoForo
    }

    private fun buildAbsoluteUrl(href: String): String {
        return if (href.startsWith("http://") || href.startsWith("https://")) href else "${properties.esaj.baseUrl}$href"
    }
}
