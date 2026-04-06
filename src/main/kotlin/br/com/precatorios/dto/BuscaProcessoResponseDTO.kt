package br.com.precatorios.dto

data class BuscaProcessoResponseDTO(
    val resultados: List<BuscaProcessoItemDTO>,
    val total: Int
)

data class BuscaProcessoItemDTO(
    val numero: String,
    val classe: String?,
    val assunto: String?,
    val foro: String?
)
