package br.com.precatorios.service

import br.com.precatorios.config.ScoringProperties
import br.com.precatorios.domain.Precatorio
import org.springframework.stereotype.Service
import java.math.BigDecimal

data class ScoredResult(
    val total: Int,
    val detalhes: Map<String, Int?>
)

@Service
class ScoringService(private val props: ScoringProperties) {

    fun score(precatorio: Precatorio): ScoredResult {
        val valor = scoreValor(precatorio.valorAtualizado)
        val entidade = scoreEntidade(precatorio.entidadeDevedora)
        val status = scoreStatus(precatorio.statusPagamento)
        val posicao = scorePosicao(precatorio.posicaoCronologica)
        val natureza = scoreNatureza(precatorio.natureza)

        // D-15: total sums only non-null criteria; null means data unavailable (D-13)
        val total = listOfNotNull(valor, entidade, status, posicao, natureza).sum()

        return ScoredResult(
            total = total,
            detalhes = mapOf(
                "valor" to valor,
                "entidadeDevedora" to entidade,
                "statusPagamento" to status,
                "posicaoCronologica" to posicao,
                "natureza" to natureza,
                "total" to total
            )
        )
    }

    // D-13: null input -> null (not 0); D-14: null in detalhes means data unavailable
    private fun scoreValor(valor: BigDecimal?): Int? {
        valor ?: return null
        val reais = valor.toLong()
        return props.valor.faixas
            .sortedByDescending { it.limiteInferior }
            .firstOrNull { reais >= it.limiteInferior }
            ?.pontos ?: 0
    }

    // Normalize to uppercase before lookup (Research Pitfall 4)
    // Uses contains to handle variations in entity name formatting
    private fun scoreEntidade(entidade: String?): Int? {
        entidade ?: return null
        val normalized = entidade.uppercase()
        return props.entidadeDevedora.mapa
            .entries
            .firstOrNull { normalized.contains(it.key.uppercase()) }
            ?.value
            ?: props.entidadeDevedora.pontosDesconhecida
    }

    // Normalize to uppercase before lookup (Research Pitfall 4)
    private fun scoreStatus(status: String?): Int? {
        status ?: return null
        val normalized = status.uppercase()
        return props.statusPagamento.mapa
            .entries
            .firstOrNull { normalized == it.key.uppercase() }
            ?.value
            ?: props.statusPagamento.pontosPadrao
    }

    private fun scorePosicao(posicao: Int?): Int? {
        posicao ?: return null
        return props.posicaoCronologica.faixas
            .firstOrNull { posicao in it.limiteInferior..it.limiteSuperior }
            ?.pontos ?: 0
    }

    private fun scoreNatureza(natureza: String?): Int? {
        natureza ?: return null
        val normalized = natureza.uppercase()
        return when {
            normalized.contains("ALIMENTAR") -> props.natureza.pontosAlimentar
            else -> props.natureza.pontosComum
        }
    }
}
