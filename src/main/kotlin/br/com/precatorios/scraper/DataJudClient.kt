package br.com.precatorios.scraper

import br.com.precatorios.config.CacheNames
import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.exception.TooManyRequestsException
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

data class DataJudResult(
    val hits: List<DataJudHit>,
    val total: Long
)

data class DataJudHit(
    val numeroProcesso: String?,
    val classe: String?,
    val assunto: String?,
    val orgaoJulgador: String?,
    val dataAjuizamento: String?,
    val raw: String
)

// ---------------------------------------------------------------------------
// Internal Jackson response model
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudResponse(
    @JsonProperty("hits") val hits: DataJudHitsWrapper
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudHitsWrapper(
    @JsonProperty("total") val total: DataJudTotal,
    @JsonProperty("hits") val hits: List<DataJudHitRaw>
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudTotal(
    @JsonProperty("value") val value: Long = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudHitRaw(
    @JsonProperty("_source") val source: DataJudSource?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudSource(
    @JsonProperty("numeroProcesso") val numeroProcesso: String?,
    @JsonProperty("classe") val classe: DataJudClasse?,
    @JsonProperty("assunto") val assunto: List<DataJudAssunto>?,
    @JsonProperty("orgaoJulgador") val orgaoJulgador: DataJudOrgao?,
    @JsonProperty("dataAjuizamento") val dataAjuizamento: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudClasse(
    @JsonProperty("nome") val nome: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudAssunto(
    @JsonProperty("nome") val nome: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DataJudOrgao(
    @JsonProperty("nome") val nome: String?
)

// ---------------------------------------------------------------------------
// Client service
// ---------------------------------------------------------------------------

@Service
class DataJudClient(
    webClientBuilder: WebClient.Builder,
    private val properties: ScraperProperties,
    @Qualifier("datajudRateLimiter") private val datajudRateLimiter: RateLimiter,
    @Qualifier("datajudRetry") private val datajudRetry: Retry
) {

    private val log = LoggerFactory.getLogger(DataJudClient::class.java)

    private val objectMapper = ObjectMapper()

    private val webClient: WebClient = webClientBuilder
        .baseUrl(properties.datajud.baseUrl)
        .defaultHeader("Authorization", "APIKey ${properties.datajud.apiKey}")
        .defaultHeader("Content-Type", "application/json")
        .build()

    /**
     * Search DataJud for processes by CNJ process number.
     * Applies rate limiting and retry via Resilience4j programmatic decoration.
     */
    @Cacheable(cacheNames = [CacheNames.DATAJUD], unless = "#result == null")
    fun buscarPorNumeroProcesso(numero: String): DataJudResult {
        val retryDecorated = Retry.decorateCheckedSupplier(datajudRetry) { doBuscarPorNumero(numero) }
        val decorated = RateLimiter.decorateCheckedSupplier(datajudRateLimiter, retryDecorated)
        return decorated.get()
    }

    /**
     * Search DataJud for processes by IBGE municipality code.
     * Applies rate limiting and retry via Resilience4j programmatic decoration.
     */
    fun buscarPorMunicipio(codigoIbge: String): DataJudResult {
        val retryDecorated = Retry.decorateCheckedSupplier(datajudRetry) { doBuscarPorMunicipio(codigoIbge) }
        val decorated = RateLimiter.decorateCheckedSupplier(datajudRateLimiter, retryDecorated)
        return decorated.get()
    }

    // ---------------------------------------------------------------------------
    // Internal fetch methods
    // ---------------------------------------------------------------------------

    internal fun doBuscarPorNumero(numero: String): DataJudResult {
        val queryBody = """{"query":{"match":{"numeroProcesso":"$numero"}},"size":${properties.datajud.maxResultadosPorPagina}}"""
        return executeQuery(queryBody)
    }

    internal fun doBuscarPorMunicipio(codigoIbge: String): DataJudResult {
        val queryBody = """{"query":{"bool":{"must":[{"term":{"codigoMunicipioIBGE":"$codigoIbge"}},{"match":{"classeProcessual":"Precatorio"}}]}},"size":${properties.datajud.maxResultadosPorPagina}}"""
        return executeQuery(queryBody)
    }

    private fun executeQuery(queryBody: String): DataJudResult {
        log.debug("DataJud query to {}: {}", properties.datajud.endpointTjsp, queryBody)

        val responseBody = try {
            webClient.post()
                .uri(properties.datajud.endpointTjsp)
                .bodyValue(queryBody)
                .exchangeToMono { response ->
                    val status = response.statusCode()
                    when {
                        status == HttpStatus.TOO_MANY_REQUESTS -> {
                            // Drain body to release connection, then signal error
                            response.releaseBody().then(
                                Mono.error(TooManyRequestsException("DataJud HTTP 429 — rate limit exceeded"))
                            )
                        }
                        status.isError -> {
                            // T-02-05: Never log or include API key in error messages
                            log.warn("DataJud request failed with status {}", status)
                            response.releaseBody().then(
                                Mono.error(ScrapingException("DataJud request failed with status $status"))
                            )
                        }
                        else -> response.bodyToMono(String::class.java)
                    }
                }
                .block(Duration.ofMillis(properties.datajud.timeoutMs.toLong()))
        } catch (e: TooManyRequestsException) {
            throw e
        } catch (e: ScrapingException) {
            throw e
        } catch (e: Exception) {
            // Reactor may wrap the cause — unwrap and rethrow domain exceptions
            val cause = e.cause
            when {
                cause is TooManyRequestsException -> throw cause
                cause is ScrapingException -> throw cause
                else -> {
                    log.warn("DataJud request error: {}", e.message)
                    throw ScrapingException("DataJud request failed: ${e.javaClass.simpleName}")
                }
            }
        }

        if (responseBody.isNullOrBlank()) {
            throw ScrapingException("DataJud returned empty response")
        }

        return parseResponse(responseBody)
    }

    private fun parseResponse(json: String): DataJudResult {
        return try {
            val response = objectMapper.readValue(json, DataJudResponse::class.java)
            val hits = response.hits.hits.mapNotNull { hitRaw ->
                val src = hitRaw.source ?: return@mapNotNull null
                DataJudHit(
                    numeroProcesso = src.numeroProcesso,
                    classe = src.classe?.nome,
                    assunto = src.assunto?.firstOrNull()?.nome,
                    orgaoJulgador = src.orgaoJulgador?.nome,
                    dataAjuizamento = src.dataAjuizamento,
                    raw = objectMapper.writeValueAsString(src)
                )
            }
            DataJudResult(hits = hits, total = response.hits.total.value)
        } catch (e: Exception) {
            log.warn("Failed to parse DataJud response: {}", e.message)
            throw ScrapingException("DataJud response parse failed: ${e.javaClass.simpleName}")
        }
    }
}
