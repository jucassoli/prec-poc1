package br.com.precatorios.dto

data class PrecatorioResponseDTO(
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
    val dadosCompletos: Boolean
)
