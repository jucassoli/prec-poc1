package br.com.precatorios.service

import br.com.precatorios.config.ScoringProperties
import br.com.precatorios.domain.Precatorio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ScoringServiceTest {

    private lateinit var service: ScoringService
    private lateinit var props: ScoringProperties

    @BeforeEach
    fun setup() {
        props = ScoringProperties() // uses default values matching application.yml
        service = ScoringService(props)
    }

    private fun precatorio(
        valorAtualizado: BigDecimal? = null,
        entidadeDevedora: String? = null,
        statusPagamento: String? = null,
        posicaoCronologica: Int? = null,
        natureza: String? = null
    ): Precatorio {
        val p = Precatorio()
        p.valorAtualizado = valorAtualizado
        p.entidadeDevedora = entidadeDevedora
        p.statusPagamento = statusPagamento
        p.posicaoCronologica = posicaoCronologica
        p.natureza = natureza
        return p
    }

    // Test 1: valor >= R$1M scores 30
    @Test
    fun `valor above 1M bracket scores 30`() {
        val result = service.score(precatorio(valorAtualizado = BigDecimal("1500000")))
        assertThat(result.detalhes["valor"]).isEqualTo(30)
    }

    // Test 2: valor R$500k-1M scores 22
    @Test
    fun `valor in 500k-1M bracket scores 22`() {
        val result = service.score(precatorio(valorAtualizado = BigDecimal("600000")))
        assertThat(result.detalhes["valor"]).isEqualTo(22)
    }

    // Test 3: null valor produces null in detalhes (D-14)
    @Test
    fun `null valor produces null in detalhes`() {
        val result = service.score(precatorio(valorAtualizado = null))
        assertThat(result.detalhes["valor"]).isNull()
    }

    // Test 4: known entity "FAZENDA DO ESTADO DE SAO PAULO" scores 25
    @Test
    fun `known entity FAZENDA DO ESTADO DE SAO PAULO scores 25`() {
        val result = service.score(precatorio(entidadeDevedora = "FAZENDA DO ESTADO DE SAO PAULO"))
        assertThat(result.detalhes["entidadeDevedora"]).isEqualTo(25)
    }

    // Test 5: unknown entity scores pontosDesconhecida (5)
    @Test
    fun `unknown entity scores pontosDesconhecida 5`() {
        val result = service.score(precatorio(entidadeDevedora = "PREFEITURA DE CAMPINAS"))
        assertThat(result.detalhes["entidadeDevedora"]).isEqualTo(5)
    }

    // Test 6: PENDENTE scores 20
    @Test
    fun `statusPagamento PENDENTE scores 20`() {
        val result = service.score(precatorio(statusPagamento = "PENDENTE"))
        assertThat(result.detalhes["statusPagamento"]).isEqualTo(20)
    }

    // Test 7: mixed case "Pendente" normalizes and scores 20 (Pitfall 4)
    @Test
    fun `statusPagamento mixed case Pendente normalizes and scores 20`() {
        val result = service.score(precatorio(statusPagamento = "Pendente"))
        assertThat(result.detalhes["statusPagamento"]).isEqualTo(20)
    }

    // Test 8: posicaoCronologica=50 (1-100 bracket) scores 15
    @Test
    fun `posicaoCronologica 50 in bracket 1-100 scores 15`() {
        val result = service.score(precatorio(posicaoCronologica = 50))
        assertThat(result.detalhes["posicaoCronologica"]).isEqualTo(15)
    }

    // Test 9: posicaoCronologica=600 (501-1000 bracket) scores 8
    @Test
    fun `posicaoCronologica 600 in bracket 501-1000 scores 8`() {
        val result = service.score(precatorio(posicaoCronologica = 600))
        assertThat(result.detalhes["posicaoCronologica"]).isEqualTo(8)
    }

    // Test 10: natureza containing "ALIMENTAR" scores 10
    @Test
    fun `natureza containing ALIMENTAR scores 10`() {
        val result = service.score(precatorio(natureza = "ALIMENTAR"))
        assertThat(result.detalhes["natureza"]).isEqualTo(10)
    }

    // Test 11: natureza="COMUM" scores 4
    @Test
    fun `natureza COMUM scores 4`() {
        val result = service.score(precatorio(natureza = "COMUM"))
        assertThat(result.detalhes["natureza"]).isEqualTo(4)
    }

    // Test 12: all fields null produces total=0 and all criterion detalhes are null (D-13, D-14)
    @Test
    fun `all fields null produces total 0 and all criterion detalhes null`() {
        val result = service.score(precatorio())
        assertThat(result.total).isEqualTo(0)
        assertThat(result.detalhes["valor"]).isNull()
        assertThat(result.detalhes["entidadeDevedora"]).isNull()
        assertThat(result.detalhes["statusPagamento"]).isNull()
        assertThat(result.detalhes["posicaoCronologica"]).isNull()
        assertThat(result.detalhes["natureza"]).isNull()
    }

    // Test 13: full precatorio with all fields produces correct total as sum of criteria
    @Test
    fun `full precatorio produces total as sum of all non-null criteria`() {
        val result = service.score(
            precatorio(
                valorAtualizado = BigDecimal("1500000"),    // 30
                entidadeDevedora = "FAZENDA DO ESTADO DE SAO PAULO", // 25
                statusPagamento = "PENDENTE",               // 20
                posicaoCronologica = 50,                    // 15
                natureza = "ALIMENTAR"                      // 10
            )
        )
        assertThat(result.total).isEqualTo(100)
        assertThat(result.detalhes["valor"]).isEqualTo(30)
        assertThat(result.detalhes["entidadeDevedora"]).isEqualTo(25)
        assertThat(result.detalhes["statusPagamento"]).isEqualTo(20)
        assertThat(result.detalhes["posicaoCronologica"]).isEqualTo(15)
        assertThat(result.detalhes["natureza"]).isEqualTo(10)
    }

    // Test 14: scoreDetalhes map contains all required keys including total (D-11)
    @Test
    fun `detalhes map contains all required keys`() {
        val result = service.score(precatorio(valorAtualizado = BigDecimal("200000")))
        assertThat(result.detalhes).containsKeys(
            "valor",
            "entidadeDevedora",
            "statusPagamento",
            "posicaoCronologica",
            "natureza",
            "total"
        )
    }
}
