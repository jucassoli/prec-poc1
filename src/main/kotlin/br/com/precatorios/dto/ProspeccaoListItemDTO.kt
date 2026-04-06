package br.com.precatorios.dto

import java.time.LocalDateTime

data class ProspeccaoListItemDTO(
    val id: Long,
    val processoSemente: String,
    val status: String,
    val processosVisitados: Int,
    val credoresEncontrados: Int,
    val leadsQualificados: Int,
    val dataInicio: LocalDateTime?,
    val dataFim: LocalDateTime?
)
