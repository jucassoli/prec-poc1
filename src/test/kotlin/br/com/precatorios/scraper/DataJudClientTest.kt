package br.com.precatorios.scraper

import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.ScrapingException
import br.com.precatorios.exception.TooManyRequestsException
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class DataJudClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: DataJudClient
    private lateinit var properties: ScraperProperties

    companion object {
        private const val SAMPLE_API_KEY = "cDZHYzlZa0JadVREZDJCendQbXY6SkJlTzNjLV9TRENyQk1RdnFKZGRQdw=="

        private val SAMPLE_RESPONSE = """
            {
              "hits": {
                "total": { "value": 1 },
                "hits": [
                  {
                    "_source": {
                      "numeroProcesso": "1234567-89.2023.8.26.0100",
                      "classe": { "nome": "Precatorio" },
                      "assunto": [{ "nome": "Precatório" }],
                      "orgaoJulgador": { "nome": "1ª Vara da Fazenda Pública" },
                      "dataAjuizamento": "2023-01-15"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        private val MUNICIPIO_RESPONSE = """
            {
              "hits": {
                "total": { "value": 2 },
                "hits": [
                  {
                    "_source": {
                      "numeroProcesso": "9876543-21.2023.8.26.0001",
                      "classe": { "nome": "Precatorio" },
                      "assunto": [{ "nome": "Precatório Municipal" }],
                      "orgaoJulgador": { "nome": "2ª Vara da Fazenda Pública" },
                      "dataAjuizamento": "2023-03-20"
                    }
                  },
                  {
                    "_source": {
                      "numeroProcesso": "1111111-11.2022.8.26.0001",
                      "classe": { "nome": "Precatorio" },
                      "assunto": [{ "nome": "Precatório Municipal" }],
                      "orgaoJulgador": null,
                      "dataAjuizamento": "2022-06-01"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        private val EMPTY_RESPONSE = """
            {
              "hits": {
                "total": { "value": 0 },
                "hits": []
              }
            }
        """.trimIndent()
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()

        properties = ScraperProperties(
            datajud = ScraperProperties.DataJudProps(
                baseUrl = baseUrl,
                endpointTjsp = "/api_publica_tjsp/_search",
                apiKey = SAMPLE_API_KEY,
                timeoutMs = 5000,
                maxResultadosPorPagina = 10
            )
        )

        val webClientBuilder = WebClient.builder()
        val rateLimiter = RateLimiter.ofDefaults("test-datajud")
        val retry = Retry.ofDefaults("test-datajud")

        client = DataJudClient(webClientBuilder, properties, rateLimiter, retry)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // -------------------------------------------------------------------------
    // buscarPorNumeroProcesso tests
    // -------------------------------------------------------------------------

    @Test
    fun `buscarPorNumeroProcesso should return result with correct total and hits`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(SAMPLE_RESPONSE)
                .addHeader("Content-Type", "application/json")
        )

        val result = client.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100")

        assertEquals(1L, result.total)
        assertEquals(1, result.hits.size)
        assertEquals("1234567-89.2023.8.26.0100", result.hits[0].numeroProcesso)
    }

    @Test
    fun `buscarPorNumeroProcesso should send Authorization header with APIKey`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(SAMPLE_RESPONSE)
                .addHeader("Content-Type", "application/json")
        )

        client.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100")

        val request = mockWebServer.takeRequest()
        val authHeader = request.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("APIKey "), "Authorization header should start with 'APIKey '")
        assertTrue(authHeader.contains("cDZHYzlZa0JadVREZDJCendQbXY6SkJlTzNjLV9TRENyQk1RdnFKZGRQdw=="))
    }

    @Test
    fun `buscarPorNumeroProcesso should send request body containing numeroProcesso`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(SAMPLE_RESPONSE)
                .addHeader("Content-Type", "application/json")
        )

        client.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100")

        val request = mockWebServer.takeRequest()
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("numeroProcesso"), "Request body should contain 'numeroProcesso'")
        assertTrue(requestBody.contains("1234567-89.2023.8.26.0100"), "Request body should contain the process number")
    }

    @Test
    fun `buscarPorNumeroProcesso should post to correct endpoint`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(SAMPLE_RESPONSE)
                .addHeader("Content-Type", "application/json")
        )

        client.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100")

        val request = mockWebServer.takeRequest()
        assertEquals("/api_publica_tjsp/_search", request.path)
        assertEquals("POST", request.method)
    }

    // -------------------------------------------------------------------------
    // buscarPorMunicipio tests
    // -------------------------------------------------------------------------

    @Test
    fun `buscarPorMunicipio should return results with correct total`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(MUNICIPIO_RESPONSE)
                .addHeader("Content-Type", "application/json")
        )

        val result = client.buscarPorMunicipio("3550308")

        assertEquals(2L, result.total)
        assertEquals(2, result.hits.size)
    }

    @Test
    fun `buscarPorMunicipio should send request body containing codigoMunicipioIBGE`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(MUNICIPIO_RESPONSE)
                .addHeader("Content-Type", "application/json")
        )

        client.buscarPorMunicipio("3550308")

        val request = mockWebServer.takeRequest()
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("codigoMunicipioIBGE"), "Request body should contain 'codigoMunicipioIBGE'")
        assertTrue(requestBody.contains("3550308"), "Request body should contain the IBGE code")
    }

    // -------------------------------------------------------------------------
    // Error handling tests
    // -------------------------------------------------------------------------

    @Test
    fun `buscarPorNumeroProcesso should throw TooManyRequestsException on HTTP 429`() {
        // Enqueue enough 429 responses for all retry attempts
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setBody("{\"error\":\"rate limit exceeded\"}")
                    .addHeader("Content-Type", "application/json")
            )
        }

        assertThrows(TooManyRequestsException::class.java) {
            client.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100")
        }
    }

    @Test
    fun `buscarPorNumeroProcesso should throw ScrapingException on empty body`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("")
                .addHeader("Content-Type", "application/json")
        )

        assertThrows(ScrapingException::class.java) {
            client.buscarPorNumeroProcesso("1234567-89.2023.8.26.0100")
        }
    }

    @Test
    fun `error messages should not contain API key value`() {
        // T-02-05: Never include API key value in exception messages
        mockWebServer.enqueue(
            MockResponse().setResponseCode(401)
        )

        val ex = assertThrows(ScrapingException::class.java) {
            client.buscarPorNumeroProcesso("any-number")
        }

        assertFalse(
            ex.message?.contains(SAMPLE_API_KEY) ?: false,
            "Exception message must not contain the API key value"
        )
    }
}
