package br.com.precatorios.scraper

import br.com.precatorios.config.CacheNames
import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.ScrapingException
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

data class PrecatorioScraped(
    val numeroPrecatorio: String?,
    val numeroProcesso: String?,
    val entidadeDevedora: String?,
    val valorOriginal: String?,
    val valorAtualizado: String?,
    val natureza: String?,
    val statusPagamento: String?,
    val posicaoCronologica: Int?,
    val dataExpedicao: String?,
    val missingFields: List<String>,
    val rawHtml: String
)

@Service
class CacScraper(
    private val properties: ScraperProperties,
    @Qualifier("cacRateLimiter") private val cacRateLimiter: RateLimiter,
    @Qualifier("cacRetry") private val cacRetry: Retry
) {

    private val log = LoggerFactory.getLogger(CacScraper::class.java)

    private val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Fetch precatorio data from CAC/SCP portal using ViewState GET/POST cycle.
     * Applies rate limiting and retry via Resilience4j programmatic decoration.
     * Renews session on blank-form response with max 1 renewal before failing.
     */
    @Cacheable(cacheNames = [CacheNames.PRECATORIOS], unless = "#result == null")
    fun fetchPrecatorio(numero: String): PrecatorioScraped {
        val retryDecorated = Retry.decorateCheckedSupplier(cacRetry) { doFetchPrecatorio(numero) }
        val decorated = RateLimiter.decorateCheckedSupplier(cacRateLimiter, retryDecorated)
        return decorated.get()
    }

    internal fun doFetchPrecatorio(numero: String): PrecatorioScraped {
        return doFetchWithRenewal(numero, sessionRenewCount = 0)
    }

    private fun doFetchWithRenewal(numero: String, sessionRenewCount: Int): PrecatorioScraped {
        // Step 1 - GET form page
        val formResponse = Jsoup.newSession()
            .timeout(properties.precatorioPortal.timeoutMs)
            .userAgent(userAgent)
            .url(properties.precatorioPortal.baseUrl)
            .method(Connection.Method.GET)
            .execute()

        val formPage = formResponse.parse()

        // Step 2 - Extract ViewState fields
        val viewState = formPage.select("input[name=__VIEWSTATE]").firstOrNull()?.attr("value") ?: ""
        val viewStateGen = formPage.select("input[name=__VIEWSTATEGENERATOR]").firstOrNull()?.attr("value") ?: ""
        val eventValidation = formPage.select("input[name=__EVENTVALIDATION]").firstOrNull()?.attr("value") ?: ""

        // Step 3 - POST with ViewState + precatorio number
        val resultResponse = Jsoup.newSession()
            .timeout(properties.precatorioPortal.timeoutMs)
            .userAgent(userAgent)
            .url(properties.precatorioPortal.baseUrl)
            .cookies(formResponse.cookies())
            .data("__VIEWSTATE", viewState)
            .data("__VIEWSTATEGENERATOR", viewStateGen)
            .data("__EVENTVALIDATION", eventValidation)
            .data("numeroPrecatorio", numero)
            .method(Connection.Method.POST)
            .execute()

        val resultPage = resultResponse.parse()

        // Step 4 - Blank-form detection (T-02-07: sessionRenewCount bounded to 1 per request)
        if (isBlankFormResponse(resultPage)) {
            if (sessionRenewCount > 0) {
                throw ScrapingException("CAC blank form after session renewal for $numero — not retryable")
            } else {
                log.warn("CAC blank form detected for {}, renewing session and retrying", numero)
                // Recursive call with incremented counter — bounded to 1 renewal
                return doFetchWithRenewal(numero, sessionRenewCount = 1)
            }
        }

        // Step 5 - Parse result page
        return parseResultPage(numero, resultPage)
    }

    internal fun isBlankFormResponse(page: Document): Boolean {
        return page.select(".resultadoPesquisa").isEmpty()
    }

    private fun parseResultPage(numero: String, page: Document): PrecatorioScraped {
        val missingFields = mutableListOf<String>()

        fun extractField(selector: String, fieldName: String): String? {
            val element = page.select(selector).firstOrNull()
            return if (element != null && element.text().isNotBlank()) {
                element.text().trim()
            } else {
                log.warn("CAC selector '{}' returned no match for precatorio {}", selector, numero)
                missingFields.add(fieldName)
                null
            }
        }

        val entidadeDevedora = extractField(".entidadeDevedora", "entidadeDevedora")
        val valorOriginal = extractField(".valorOriginal", "valorOriginal")
        val valorAtualizado = extractField(".valorAtualizado", "valorAtualizado")
        val natureza = extractField(".natureza", "natureza")
        val statusPagamento = extractField(".statusPagamento", "statusPagamento")
        val dataExpedicao = extractField(".dataExpedicao", "dataExpedicao")

        val posicaoCronologicaStr = extractField(".posicaoCronologica", "posicaoCronologica")
        val posicaoCronologica = posicaoCronologicaStr?.trim()?.toIntOrNull()

        // Step 6 - Return PrecatorioScraped with all fields and rawHtml
        return PrecatorioScraped(
            numeroPrecatorio = numero,
            numeroProcesso = null,
            entidadeDevedora = entidadeDevedora,
            valorOriginal = valorOriginal,
            valorAtualizado = valorAtualizado,
            natureza = natureza,
            statusPagamento = statusPagamento,
            posicaoCronologica = posicaoCronologica,
            dataExpedicao = dataExpedicao,
            missingFields = missingFields,
            rawHtml = page.outerHtml()
        )
    }
}
