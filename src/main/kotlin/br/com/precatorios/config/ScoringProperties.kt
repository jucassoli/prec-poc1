package br.com.precatorios.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "scoring")
data class ScoringProperties(
    val valor: ValorProps = ValorProps(),
    val entidadeDevedora: EntidadeDevedoraProps = EntidadeDevedoraProps(),
    val statusPagamento: StatusPagamentoProps = StatusPagamentoProps(),
    val posicaoCronologica: PosicaoProps = PosicaoProps(),
    val natureza: NaturezaProps = NaturezaProps()
) {
    data class ValorProps(
        val maxPontos: Int = 30,
        val faixas: List<FaixaValor> = listOf(
            FaixaValor(1_000_000, 30),
            FaixaValor(500_000, 22),
            FaixaValor(150_000, 15),
            FaixaValor(50_000, 8),
            FaixaValor(0, 3)
        )
    )
    data class FaixaValor(val limiteInferior: Long = 0, val pontos: Int = 0)

    data class EntidadeDevedoraProps(
        val maxPontos: Int = 25,
        val pontosDesconhecida: Int = 5,
        val mapa: Map<String, Int> = mapOf(
            "FAZENDA DO ESTADO DE SAO PAULO" to 25,
            "PREFEITURA DO MUNICIPIO DE SAO PAULO" to 20
        )
    )

    data class StatusPagamentoProps(
        val maxPontos: Int = 20,
        val pontosPadrao: Int = 0,
        val mapa: Map<String, Int> = mapOf(
            "PENDENTE" to 20,
            "EM PROCESSAMENTO" to 15,
            "PARCIALMENTE PAGO" to 10,
            "PAGO" to 0
        )
    )

    data class PosicaoProps(
        val maxPontos: Int = 15,
        val faixas: List<FaixaPosicao> = listOf(
            FaixaPosicao(1, 100, 15),
            FaixaPosicao(101, 500, 12),
            FaixaPosicao(501, 1000, 8),
            FaixaPosicao(1001, 5000, 4),
            FaixaPosicao(5001, Int.MAX_VALUE, 1)
        )
    )
    data class FaixaPosicao(
        val limiteInferior: Int = 0,
        val limiteSuperior: Int = Int.MAX_VALUE,
        val pontos: Int = 0
    )

    data class NaturezaProps(
        val maxPontos: Int = 10,
        val pontosAlimentar: Int = 10,
        val pontosComum: Int = 4
    )
}
