package br.com.precatorios.scraper

import br.com.precatorios.config.ScraperProperties
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for EsajScraper parsing logic.
 *
 * Tests exercise the internal parse* methods directly against HTML fixture files,
 * avoiding live network calls. Live/integration validation is deferred to Plan 02-04.
 */
class EsajScraperTest {

    private lateinit var scraper: EsajScraper

    @BeforeEach
    fun setUp() {
        val properties = ScraperProperties(
            esaj = ScraperProperties.EsajProps(
                baseUrl = "https://esaj.tjsp.jus.br",
                delayMs = 0,
                timeoutMs = 5000,
                userAgent = "Mozilla/5.0 Test Agent",
                maxRetries = 3
            )
        )
        val rateLimiter = RateLimiter.ofDefaults("test-esaj")
        val retry = Retry.ofDefaults("test-esaj")
        scraper = EsajScraper(properties, rateLimiter, retry)
    }

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    private fun loadFixture(filename: String): String {
        val resource = javaClass.classLoader.getResourceAsStream("fixtures/$filename")
            ?: throw IllegalArgumentException("Fixture not found: fixtures/$filename")
        return resource.bufferedReader().readText()
    }

    // -------------------------------------------------------------------------
    // fetchProcesso parsing — process fixture
    // -------------------------------------------------------------------------

    @Test
    fun `fetchProcesso parses classe from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("Procedimento Comum", result.classe)
    }

    @Test
    fun `fetchProcesso parses assunto from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("Precatorio", result.assunto)
    }

    @Test
    fun `fetchProcesso parses foro from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("Foro Central", result.foro)
    }

    @Test
    fun `fetchProcesso parses vara from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("1a Vara da Fazenda Publica", result.vara)
    }

    @Test
    fun `fetchProcesso parses juiz from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("Dr. Teste Silva", result.juiz)
    }

    @Test
    fun `fetchProcesso parses valorAcao from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("R$ 150.000,00", result.valorAcao)
    }

    @Test
    fun `fetchProcesso extracts parties from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.partes.size >= 2, "Expected at least 2 parties, got ${result.partes.size}")
        assertTrue(result.partes[0].nome.isNotBlank(), "First party nome should not be blank")
    }

    @Test
    fun `fetchProcesso extracts first party nome correctly`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("JOAO DA SILVA", result.partes[0].nome)
    }

    @Test
    fun `fetchProcesso extracts party tipo correctly`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("Reqte", result.partes[0].tipo)
    }

    @Test
    fun `fetchProcesso extracts party advogado correctly`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertNotNull(result.partes[0].advogado)
        assertTrue(result.partes[0].advogado!!.contains("DR. ADVOGADO TESTE"))
    }

    @Test
    fun `fetchProcesso extracts incidents from fixture HTML`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.incidentes.isNotEmpty(), "Expected at least 1 incident")
        assertNotNull(result.incidentes[0].numero)
    }

    @Test
    fun `fetchProcesso extracts incident numero correctly`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("INC001", result.incidentes[0].numero)
    }

    @Test
    fun `fetchProcesso extracts incident descricao correctly`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertEquals("Embargos de Declaracao", result.incidentes[0].descricao)
    }

    @Test
    fun `fetchProcesso has empty missingFields when all selectors match`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.missingFields.isEmpty(), "missingFields should be empty, got: ${result.missingFields}")
    }

    @Test
    fun `fetchProcesso stores rawHtml in result`() {
        val doc = Jsoup.parse(loadFixture("esaj_processo.html"))
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.rawHtml.isNotBlank(), "rawHtml should not be blank")
        assertTrue(result.rawHtml.contains("classeProcesso"), "rawHtml should contain fixture content")
    }

    // -------------------------------------------------------------------------
    // Graceful degradation — empty HTML fixture
    // -------------------------------------------------------------------------

    @Test
    fun `fetchProcesso returns null classe on empty HTML`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertNull(result.classe)
    }

    @Test
    fun `fetchProcesso populates missingFields on empty HTML`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.missingFields.contains("classe"), "missingFields should contain 'classe'")
        assertTrue(result.missingFields.contains("assunto"), "missingFields should contain 'assunto'")
        assertTrue(result.missingFields.contains("foro"), "missingFields should contain 'foro'")
        assertTrue(result.missingFields.contains("vara"), "missingFields should contain 'vara'")
        assertTrue(result.missingFields.contains("juiz"), "missingFields should contain 'juiz'")
        assertTrue(result.missingFields.contains("valorAcao"), "missingFields should contain 'valorAcao'")
    }

    @Test
    fun `fetchProcesso returns empty partes on empty HTML`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.partes.isEmpty(), "partes should be empty for empty HTML")
    }

    @Test
    fun `fetchProcesso returns empty incidentes on empty HTML`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = scraper.parseProcesso("1001234-56.2020.8.26.0100", doc)

        assertTrue(result.incidentes.isEmpty(), "incidentes should be empty for empty HTML")
    }

    // -------------------------------------------------------------------------
    // buscarPorNome parsing — search fixture
    // -------------------------------------------------------------------------

    @Test
    fun `buscarPorNome returns non-empty list from search fixture`() {
        val doc = Jsoup.parse(loadFixture("esaj_busca.html"))
        val result = scraper.parseBusca(doc)

        assertTrue(result.isNotEmpty(), "Search results should not be empty")
    }

    @Test
    fun `buscarPorNome returns correct numero in first result`() {
        val doc = Jsoup.parse(loadFixture("esaj_busca.html"))
        val result = scraper.parseBusca(doc)

        assertTrue(result[0].numero.isNotBlank(), "First result numero should not be blank")
        assertEquals("1001234-56.2020.8.26.0100", result[0].numero)
    }

    @Test
    fun `buscarPorNome returns correct classe in first result`() {
        val doc = Jsoup.parse(loadFixture("esaj_busca.html"))
        val result = scraper.parseBusca(doc)

        assertEquals("Procedimento Comum", result[0].classe)
    }

    @Test
    fun `buscarPorNome returns multiple results from fixture`() {
        val doc = Jsoup.parse(loadFixture("esaj_busca.html"))
        val result = scraper.parseBusca(doc)

        assertEquals(2, result.size, "Expected 2 results from fixture")
    }

    @Test
    fun `buscarPorNome returns empty list on empty search page`() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val result = scraper.parseBusca(doc)

        assertTrue(result.isEmpty(), "Empty HTML should return empty results")
    }
}
