package br.com.precatorios.config

import br.com.precatorios.scraper.BuscaResultado
import br.com.precatorios.scraper.CacScraper
import br.com.precatorios.scraper.DataJudClient
import br.com.precatorios.scraper.DataJudHit
import br.com.precatorios.scraper.DataJudResult
import br.com.precatorios.scraper.EsajScraper
import br.com.precatorios.scraper.IncidenteScraped
import br.com.precatorios.scraper.ParteScraped
import br.com.precatorios.scraper.PrecatorioScraped
import br.com.precatorios.scraper.ProcessoScraped
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.cache.CacheManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@Testcontainers
class CacheConfigTest {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @SpykBean
    lateinit var esajScraper: EsajScraper

    @SpykBean
    lateinit var cacScraper: CacScraper

    @SpykBean
    lateinit var dataJudClient: DataJudClient

    @Autowired
    lateinit var cacheManager: CacheManager

    private val testProcesso = ProcessoScraped(
        numero = "1234567-89.2020.8.26.0100",
        classe = "Procedimento Comum Cível",
        assunto = "Precatório",
        foro = "Foro Central Cível",
        vara = "1ª Vara",
        juiz = "Dr. Silva",
        valorAcao = "R$ 100.000,00",
        partes = emptyList<ParteScraped>(),
        incidentes = emptyList<IncidenteScraped>(),
        missingFields = emptyList(),
        rawHtml = "<html/>"
    )

    private val testPrecatorio = PrecatorioScraped(
        numeroPrecatorio = "2020/00123",
        numeroProcesso = "1234567-89.2020.8.26.0100",
        entidadeDevedora = "Estado de São Paulo",
        valorOriginal = "R$ 50.000,00",
        valorAtualizado = "R$ 60.000,00",
        natureza = "Alimentar",
        statusPagamento = "Pendente",
        posicaoCronologica = 5,
        dataExpedicao = "01/01/2020",
        missingFields = emptyList(),
        rawHtml = "<html/>"
    )

    private val testDataJudResult = DataJudResult(
        hits = listOf(
            DataJudHit(
                numeroProcesso = "1234567-89.2020.8.26.0100",
                classe = "Precatório",
                assunto = "Pagamento",
                orgaoJulgador = "1ª Vara",
                dataAjuizamento = "2020-01-01",
                raw = "{}"
            )
        ),
        total = 1
    )

    @BeforeEach
    fun clearCaches() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
    }

    @Test
    fun `fetchProcesso second call returns cached result without scraper invocation`() {
        every { esajScraper.doFetchProcesso("1234567-89.2020.8.26.0100") } returns testProcesso

        val first = esajScraper.fetchProcesso("1234567-89.2020.8.26.0100")
        val second = esajScraper.fetchProcesso("1234567-89.2020.8.26.0100")

        verify(exactly = 1) { esajScraper.doFetchProcesso("1234567-89.2020.8.26.0100") }
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `fetchPrecatorio second call returns cached result without scraper invocation`() {
        every { cacScraper.doFetchPrecatorio("2020/00123") } returns testPrecatorio

        val first = cacScraper.fetchPrecatorio("2020/00123")
        val second = cacScraper.fetchPrecatorio("2020/00123")

        verify(exactly = 1) { cacScraper.doFetchPrecatorio("2020/00123") }
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `buscarPorNumeroProcesso second call returns cached result without scraper invocation`() {
        every { dataJudClient.doBuscarPorNumero("1234567-89.2020.8.26.0100") } returns testDataJudResult

        val first = dataJudClient.buscarPorNumeroProcesso("1234567-89.2020.8.26.0100")
        val second = dataJudClient.buscarPorNumeroProcesso("1234567-89.2020.8.26.0100")

        verify(exactly = 1) { dataJudClient.doBuscarPorNumero("1234567-89.2020.8.26.0100") }
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `two different process numbers create separate cache entries`() {
        every { esajScraper.doFetchProcesso(any()) } returns testProcesso

        esajScraper.fetchProcesso("1111111-11.2020.8.26.0100")
        esajScraper.fetchProcesso("2222222-22.2020.8.26.0100")

        verify(exactly = 2) { esajScraper.doFetchProcesso(any()) }
    }
}
