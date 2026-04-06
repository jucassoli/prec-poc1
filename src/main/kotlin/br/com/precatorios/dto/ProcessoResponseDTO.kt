package br.com.precatorios.dto

data class ProcessoResponseDTO(
    val numero: String,
    val classe: String?,
    val assunto: String?,
    val foro: String?,
    val vara: String?,
    val juiz: String?,
    val valorAcao: String?,
    val partes: List<ParteDTO>,
    val incidentes: List<IncidenteDTO>,
    val missingFields: List<String>,
    val dadosCompletos: Boolean
)
