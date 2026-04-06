package br.com.precatorios.dto

data class DataJudBuscarResponseDTO(
    val total: Long,
    val resultados: List<DataJudResultadoDTO>
)

data class DataJudResultadoDTO(
    val numeroProcesso: String?,
    val classe: String?,
    val assunto: String?,
    val orgaoJulgador: String?,
    val dataAjuizamento: String?
)
