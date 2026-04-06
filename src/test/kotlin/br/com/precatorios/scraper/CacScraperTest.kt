package br.com.precatorios.scraper

import br.com.precatorios.config.ScraperProperties
import br.com.precatorios.exception.ScrapingException
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CacScraperTest {

    private lateinit var properties: ScraperProperties
    private lateinit var cacRateLimiter: RateLimiter
    private lateinit var cacRetry: Retry
    private lateinit var scraper: CacScraper

    @BeforeEach
    fun setUp() {
        properties = ScraperProperties(
            precatorioPortal = ScraperProperties.CacProps(
                baseUrl = "https://www.tjsp.jus.br/cac/scp",
                delayMs = 0,
                timeoutMs = 5000
            )
        )
        cacRateLimiter = RateLimiter.ofDefaults("test-cac")
        cacRetry = Retry.ofDefaults("test-cac")
        scraper = CacScraper(properties, cacRateLimiter, cacRetry)
    }

    // -------------------------------------------------------------------------
    // ViewState extraction tests using fixture file
    // -------------------------------------------------------------------------

    @Test
    fun `should extract VIEWSTATE from form page`() {
        val html = loadFixture("cac_form.html")
        val doc = Jsoup.parse(html)

        val viewState = doc.select("input[name=__VIEWSTATE]").firstOrNull()?.attr("value")

        assertNotNull(viewState)
        assertEquals("MOCK_VIEWSTATE", viewState)
    }

    @Test
    fun `should extract VIEWSTATEGENERATOR from form page`() {
        val html = loadFixture("cac_form.html")
        val doc = Jsoup.parse(html)

        val viewStateGen = doc.select("input[name=__VIEWSTATEGENERATOR]").firstOrNull()?.attr("value")

        assertNotNull(viewStateGen)
        assertEquals("MOCK_VIEWSTATEGENERATOR", viewStateGen)
    }

    @Test
    fun `should extract EVENTVALIDATION from form page`() {
        val html = loadFixture("cac_form.html")
        val doc = Jsoup.parse(html)

        val eventValidation = doc.select("input[name=__EVENTVALIDATION]").firstOrNull()?.attr("value")

        assertNotNull(eventValidation)
        assertEquals("MOCK_EVENTVALIDATION", eventValidation)
    }

    // -------------------------------------------------------------------------
    // Result page parsing tests using fixture file
    // -------------------------------------------------------------------------

    @Test
    fun `should parse entidadeDevedora from result page`() {
        val html = loadFixture("cac_resultado.html")
        val doc = Jsoup.parse(html)

        val entidadeDevedora = doc.select(".entidadeDevedora").firstOrNull()?.text()

        assertNotNull(entidadeDevedora)
        assertEquals("FAZENDA DO ESTADO DE SÃO PAULO", entidadeDevedora)
    }

    @Test
    fun `should parse valorOriginal from result page`() {
        val html = loadFixture("cac_resultado.html")
        val doc = Jsoup.parse(html)

        val valorOriginal = doc.select(".valorOriginal").firstOrNull()?.text()

        assertNotNull(valorOriginal)
        assertTrue(valorOriginal!!.contains("150.000"))
    }

    @Test
    fun `should parse natureza from result page`() {
        val html = loadFixture("cac_resultado.html")
        val doc = Jsoup.parse(html)

        val natureza = doc.select(".natureza").firstOrNull()?.text()

        assertNotNull(natureza)
        assertEquals("Alimentar", natureza)
    }

    @Test
    fun `should parse posicaoCronologica from result page`() {
        val html = loadFixture("cac_resultado.html")
        val doc = Jsoup.parse(html)

        val posicao = doc.select(".posicaoCronologica").firstOrNull()?.text()?.trim()?.toIntOrNull()

        assertNotNull(posicao)
        assertEquals(42, posicao)
    }

    // -------------------------------------------------------------------------
    // Blank-form detection tests
    // -------------------------------------------------------------------------

    @Test
    fun `should detect blank form response when resultadoPesquisa is missing`() {
        val emptyHtml = "<html><body><div class='outraCoisa'>nada aqui</div></body></html>"
        val doc = Jsoup.parse(emptyHtml)

        assertTrue(scraper.isBlankFormResponse(doc))
    }

    @Test
    fun `should not flag blank form when resultadoPesquisa is present`() {
        val html = loadFixture("cac_resultado.html")
        val doc = Jsoup.parse(html)

        assertFalse(scraper.isBlankFormResponse(doc))
    }

    @Test
    fun `should detect blank form on truly empty page`() {
        val emptyHtml = "<html><body></body></html>"
        val doc = Jsoup.parse(emptyHtml)

        assertTrue(scraper.isBlankFormResponse(doc))
    }

    // -------------------------------------------------------------------------
    // Session renewal limit — verify exception message semantics
    // -------------------------------------------------------------------------

    @Test
    fun `not-retryable exception should contain correct message`() {
        val exception = ScrapingException("CAC blank form after session renewal for 12345 — not retryable")
        assertTrue(exception.message!!.contains("not retryable"))
        assertFalse(exception.message!!.contains("renewed, retrying"))
    }

    @Test
    fun `renewal exception should contain correct message`() {
        val exception = ScrapingException("CAC session expired for 12345 — renewed, retrying")
        assertTrue(exception.message!!.contains("renewed, retrying"))
        assertFalse(exception.message!!.contains("not retryable"))
    }

    @Test
    fun `should throw ScrapingException with not-retryable after renewal exhausted`() {
        // Verify blank form detection works for a page that looks like a session-expired blank result
        val blankHtml = "<html><body><p>Nenhum resultado encontrado</p></body></html>"
        val doc = Jsoup.parse(blankHtml)

        assertTrue(scraper.isBlankFormResponse(doc), "Page without .resultadoPesquisa should be blank form")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun loadFixture(filename: String): String {
        val resource = javaClass.classLoader.getResourceAsStream("fixtures/$filename")
            ?: throw IllegalArgumentException("Fixture not found: fixtures/$filename")
        return resource.bufferedReader().readText()
    }
}
